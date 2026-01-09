package me.sparklee.LivesSMP.utils;

import me.sparklee.LivesSMP.LivesSMP;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Locale;
import java.util.logging.Level;

public final class DebugLog {

    private DebugLog() {}

    public static boolean enabled(Plugin plugin) {
        return plugin.getConfig().getBoolean("debug.enabled", false);
    }

    public static void d(Plugin plugin, String message) {
        if (!enabled(plugin)) return;
        plugin.getLogger().info("[DEBUG] " + message);
    }

    public static void d(Plugin plugin, String message, Throwable t) {
        if (!enabled(plugin)) return;
        plugin.getLogger().log(Level.INFO, "[DEBUG] " + message, t);
    }

    public static void memory(Plugin plugin, String context) {
        if (!enabled(plugin)) return;

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();

        long heapUsed = heap.getUsed();
        long heapCommitted = heap.getCommitted();
        long heapMax = heap.getMax();

        long nonHeapUsed = nonHeap.getUsed();
        long nonHeapCommitted = nonHeap.getCommitted();

        String base = String.format(Locale.ROOT,
                "[DEBUG] MEM %s | heap used=%sMB committed=%sMB max=%sMB | nonheap used=%sMB committed=%sMB | online=%d",
                context,
                mb(heapUsed),
                mb(heapCommitted),
                heapMax <= 0 ? "?" : mb(heapMax),
                mb(nonHeapUsed),
                mb(nonHeapCommitted),
                Bukkit.getOnlinePlayers().size()
        );

        if (plugin instanceof LivesSMP livesSMP && livesSMP.getPlayerManager() != null) {
            int pendingTeleports = livesSMP.getPlayerManager().getPendingReviveTeleportsCount();
            plugin.getLogger().info(base + " | pendingReviveTeleports=" + pendingTeleports);
        } else {
            plugin.getLogger().info(base);
        }
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", (bytes / 1024.0 / 1024.0));
    }
}
