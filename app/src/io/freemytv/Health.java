package io.freemytv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen's device health: memory in use, the TV apps left running in the background and the chip's temperature,
 * read every 30 s while the home screen is visible, and "free memory" (am kill-all: only the background processes the
 * system may stop). The apps, the temperature and the cleanup go through the TV's own ADB (HomeMode's key), only if it's
 * already allowed: the widget never brings up the debugging prompt. Without it, just the memory. The temperature comes
 * from Android's thermal service (the sensor files are closed even to the shell on TV boxes).
 */
final class Health {
    interface Listener { void onHealth(); }

    static final long EVERY = 30_000;

    volatile long totalMb, availMb;
    volatile int backgroundApps = -1;     // -1: unknown (no ADB)
    volatile float cpuTemp = Float.NaN, throttleTemp = Float.NaN;     // °C; NaN: unknown

    private final Context context;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;
    private long readAt;

    Health(Context c, Listener listener) {
        context = c.getApplicationContext();
        this.listener = listener;
    }

    /** Reads again if the last reading is older than EVERY (or `now`). */
    void refresh(boolean now) {
        long t = System.currentTimeMillis();
        if (busy || (!now && t - readAt < EVERY)) return;
        busy = true;
        readAt = t;
        io.execute(() -> {
            try {
                read();
            } finally {
                busy = false;
                main.post(listener::onHealth);
            }
        });
    }

    /**
     * Closes the TV apps left running (force-stop: am kill-all alone spares the previous app and anything with a
     * service), then the cached rest; reads again and gives `done` the memory freed (MB, main thread; -1 without ADB).
     */
    void free(java.util.function.IntConsumer done) {
        io.execute(() -> {
            long before = memory();
            boolean ok = false;
            try (Adb adb = Adb.connectQuiet(context)) {
                if (adb != null) {
                    for (String pkg : runningTvApps(adb)) adb.shell("am force-stop " + pkg);
                    adb.shell("am kill-all");
                    ok = true;
                }
            } catch (Exception e) {
                AppLog.w("free memory: " + e.getMessage());
            }
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}     // the system settles
            read();
            readAt = System.currentTimeMillis();
            int freed = ok ? (int) Math.max(0, availMb - before) : -1;
            main.post(() -> {
                listener.onHealth();
                done.accept(freed);
            });
        });
    }

    private long memory() {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am == null) return availMb;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        totalMb = mi.totalMem / (1024 * 1024);
        return mi.availMem / (1024 * 1024);
    }

    private void read() {
        availMb = memory();
        try (Adb adb = Adb.connectQuiet(context)) {
            if (adb == null) {
                backgroundApps = -1;
                cpuTemp = Float.NaN;
                return;
            }
            String hw = adb.shell("dumpsys hardware_properties");
            cpuTemp = first(hw, "CPU temperatures:");
            throttleTemp = first(hw, "CPU throttling temperatures:");
            backgroundApps = runningTvApps(adb).size();
        } catch (Exception e) {
            backgroundApps = -1;
            cpuTemp = Float.NaN;
        }
    }

    /** The first number of a "Name: [56.0, ...]" line in the dump, or NaN. */
    private static float first(String dump, String name) {
        int at = dump.indexOf(name);
        if (at < 0) return Float.NaN;
        int open = dump.indexOf('[', at), close = dump.indexOf(']', open + 1);
        if (open < 0 || close < 0) return Float.NaN;
        String list = dump.substring(open + 1, close).trim();
        if (list.isEmpty()) return Float.NaN;
        try {
            return Float.parseFloat(list.split(",")[0].trim());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    /** The TV apps (the ones with an icon) that have a process, not counting Lume. */
    private Set<String> runningTvApps(Adb adb) throws java.io.IOException {
        Set<String> tvApps = tvApps();
        Set<String> running = new HashSet<>();
        for (String line : adb.shell("ps -A -o USER,NAME").split("\n")) {
            String[] cols = line.trim().split("\\s+");
            if (cols.length < 2 || !cols[0].startsWith("u0_a")) continue;
            String pkg = cols[1].contains(":") ? cols[1].substring(0, cols[1].indexOf(':')) : cols[1];
            if (tvApps.contains(pkg)) running.add(pkg);
        }
        return running;
    }

    private Set<String> tvApps() {
        Set<String> out = new HashSet<>();
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) out.add(ri.activityInfo.packageName);
        out.remove(context.getPackageName());
        return out;
    }

    void shutdown() {
        io.shutdownNow();
    }
}
