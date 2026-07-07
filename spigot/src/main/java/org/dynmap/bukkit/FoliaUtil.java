package org.dynmap.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia compatibility layer — pure reflection, no Folia API JAR needed at compile time.
 * Dynmap compiles against old Spigot API; Folia scheduler methods are called at runtime only.
 */
public final class FoliaUtil {

    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            folia = false;
        }
        FOLIA = folia;
    }

    private FoliaUtil() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    /** Runs {@code task} on the global region scheduler immediately. */
    public static void runGlobalSync(Plugin plugin, Runnable task) {
        try {
            Object s = globalScheduler();
            s.getClass().getMethod("run", Plugin.class, Consumer.class)
                    .invoke(s, plugin, (Consumer<Object>) t -> task.run());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Runs {@code task} on the global region scheduler after {@code delayTicks} ticks. */
    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        try {
            Object s = globalScheduler();
            s.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
                    .invoke(s, plugin, (Consumer<Object>) t -> task.run(), delayTicks);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Runs a repeating task on the global region scheduler; returns the opaque task handle. */
    public static Object runGlobalTimer(Plugin plugin, Consumer<Object> consumer, long initialDelay, long period) {
        try {
            Object s = globalScheduler();
            return s.getClass()
                    .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class)
                    .invoke(s, plugin, consumer, initialDelay, period);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Submits {@code task} to the global region scheduler and returns a Future for the result.
     * Replaces {@code BukkitScheduler.callSyncMethod()} on Folia servers.
     */
    public static <T> Future<T> callGlobalSync(Plugin plugin, java.util.concurrent.Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runGlobalSync(plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** Cancels all tasks on both global region and async schedulers for the given plugin. */
    public static void cancelAllTasks(Plugin plugin) {
        try {
            Object gs = globalScheduler();
            gs.getClass().getMethod("cancelTasks", Plugin.class).invoke(gs, plugin);
        } catch (Exception ignored) {}
        try {
            Object as = asyncScheduler();
            as.getClass().getMethod("cancelTasks", Plugin.class).invoke(as, plugin);
        } catch (Exception ignored) {}
    }

    /** Cancels a single opaque task handle returned by {@link #runGlobalTimer}. */
    public static void cancelTask(Object task) {
        if (task == null) return;
        try {
            task.getClass().getMethod("cancel").invoke(task);
        } catch (Exception ignored) {}
    }

    private static Object globalScheduler() throws Exception {
        return Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
    }

    private static Object asyncScheduler() throws Exception {
        return Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
    }
}
