package me.sparklee.LivesSMP;

import me.sparklee.LivesSMP.commands.LivesCommand;
import me.sparklee.LivesSMP.commands.ReloadCommand;
import me.sparklee.LivesSMP.commands.ReviveCommand;
import me.sparklee.LivesSMP.events.DeathListener;
import me.sparklee.LivesSMP.events.JoinListener;
import me.sparklee.LivesSMP.events.CraftingListener;
import me.sparklee.LivesSMP.events.RespawnTeleportListener;
import me.sparklee.LivesSMP.items.ReviveItem;
import me.sparklee.LivesSMP.utils.MessageManager;
import me.sparklee.LivesSMP.data.DatabaseManager;
import me.sparklee.LivesSMP.data.PlayerManager;
import me.sparklee.LivesSMP.commands.MainCommand;
import me.sparklee.LivesSMP.utils.ConfigManager;
import me.sparklee.LivesSMP.commands.AddLivesCommand;
import me.sparklee.LivesSMP.commands.RemoveLivesCommand;
import me.sparklee.LivesSMP.commands.SetLivesCommand;
import me.sparklee.LivesSMP.commands.TopLivesCommand;
import me.sparklee.LivesSMP.utils.UpdateChecker;
import me.sparklee.LivesSMP.utils.LivesExpansion;
import me.sparklee.LivesSMP.utils.DebugLog;
import me.sparklee.LivesSMP.tasks.DebugMemorySamplerTask;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.PluginCommand;

public class LivesSMP extends JavaPlugin {

    private static LivesSMP instance;
    private PlayerManager playerManager;
    private ReviveItem reviveItem;
    private DatabaseManager databaseManager;
    private BukkitTask actionBarTask;
    private BukkitTask debugMemoryTask;
    private BukkitTask reviveTeleportCleanupTask;
    private LivesExpansion livesExpansion;

    @Override
    public void onEnable() {
        instance = this;

        String version = getDescription().getVersion();

        getLogger().info("=======================================");
        getLogger().info("     Enabling LivesSMP v" + version);
        getLogger().info("=======================================");

        DebugLog.memory(this, "onEnable:start");

        // Initialize version-aware config manager
        ConfigManager configManager = new ConfigManager(this, "config.yml");
        configManager.load(); // Handles version check, backups, and updates

        DebugLog.d(this, "debug.enabled=" + getConfig().getBoolean("debug.enabled", false));
        DebugLog.d(this, "mysql.enabled=" + getConfig().getBoolean("mysql.enabled", false));
        DebugLog.d(this, "actionbar.enabled=" + getConfig().getBoolean("actionbar.enabled", true));

        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        DebugLog.memory(this, "onEnable:after-db");

        playerManager = new PlayerManager(this);
        reviveItem = new ReviveItem(this);
        MessageManager.load();

        DebugLog.memory(this, "onEnable:after-managers");

        // Periodic cleanup for revive-teleport flags (prevents unbounded growth if revived players never re-join)
        if (getConfig().getBoolean("revive-teleport.cleanup.enabled", true)) {
            int minutes = Math.max(5, getConfig().getInt("revive-teleport.cleanup.interval-minutes", 60));
            long periodTicks = minutes * 60L * 20L;
            reviveTeleportCleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(
                    this,
                    () -> {
                        int removed = 0;
                        try {
                            removed = playerManager.pruneExpiredReviveTeleports(true);
                        } catch (Exception e) {
                            DebugLog.d(this, "revive-teleport cleanup failed", e);
                        }
                        if (removed > 0) {
                            DebugLog.d(this, "revive-teleport cleanup: removed=" + removed + " pending=" + playerManager.getPendingReviveTeleportsCount());
                        }
                    },
                    periodTicks,
                    periodTicks
            );
            DebugLog.d(this, "revive-teleport cleanup enabled (interval=" + minutes + "m)");
        }

        // Check for updates on Spigot
        new me.sparklee.LivesSMP.utils.UpdateChecker(this, 130095).checkForUpdates();
        if (getConfig().getBoolean("check-for-updates", true)) {
            UpdateChecker checker = new UpdateChecker(this, 130095);
            getServer().getPluginManager().registerEvents(checker, this);
            checker.checkForUpdates();
        }


        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
    getServer().getPluginManager().registerEvents(new RespawnTeleportListener(this), this);


        ReviveCommand reviveCommand = new ReviveCommand(this);
        PluginCommand revive = getCommand("revive");
        if (revive != null) {
            revive.setExecutor(reviveCommand);
            revive.setTabCompleter(reviveCommand);
        }
        getCommand("lives").setExecutor(new LivesCommand(this));
        getCommand("livessmpreload").setExecutor(new ReloadCommand(this));
        getCommand("livessmp").setExecutor(new MainCommand(this));
        getCommand("addlives").setExecutor(new AddLivesCommand(this));
        getCommand("removelives").setExecutor(new RemoveLivesCommand(this));
        getCommand("setlives").setExecutor(new SetLivesCommand(this));
        getCommand("toplives").setExecutor(new TopLivesCommand(this));

        // Start ActionBar life display
        if (getConfig().getBoolean("actionbar.enabled", true)) {
            int interval = getConfig().getInt("actionbar.interval-ticks", 60);
            // Run sync: avoids calling Bukkit API off-thread.
            actionBarTask = getServer().getScheduler().runTaskTimer(this, new me.sparklee.LivesSMP.tasks.ActionBarTask(this), 0L, interval);
            getLogger().info("ActionBar life display enabled (interval: " + interval + " ticks)");
        }

        // Optional periodic memory sampler (debug)
        if (getConfig().getBoolean("debug.enabled", false)
                && getConfig().getBoolean("debug.memory-sampler.enabled", false)) {
            int seconds = Math.max(5, getConfig().getInt("debug.memory-sampler.interval-seconds", 60));
            long periodTicks = seconds * 20L;
            debugMemoryTask = getServer().getScheduler().runTaskTimerAsynchronously(
                    this,
                    new DebugMemorySamplerTask(this),
                    periodTicks,
                    periodTicks
            );
            DebugLog.d(this, "Memory sampler enabled (interval=" + seconds + "s)");
        }

        // Register PlaceholderAPI expansion if PAPI is installed
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            livesExpansion = new LivesExpansion(this);
            livesExpansion.register();
            getLogger().info("PlaceholderAPI detected - registered placeholders!");
        } else {
            getLogger().info("PlaceholderAPI not found - skipping placeholder registration.");
        }


        getLogger().info("LivesSMP v" + version + " has been enabled successfully!");

        DebugLog.memory(this, "onEnable:done");
    }

    @Override
    public void onDisable() {
        String version = getDescription().getVersion();

        DebugLog.memory(this, "onDisable:start");

        // Extra safety: cancel any remaining scheduled tasks for this plugin.
        try {
            getServer().getScheduler().cancelTasks(this);
        } catch (Exception ignored) {
        }

        if (debugMemoryTask != null) {
            debugMemoryTask.cancel();
            debugMemoryTask = null;
        }

        if (reviveTeleportCleanupTask != null) {
            reviveTeleportCleanupTask.cancel();
            reviveTeleportCleanupTask = null;
        }

        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }

        if (livesExpansion != null) {
            try {
                livesExpansion.unregister();
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
            livesExpansion = null;
        }

        try {
            MessageManager.clear();
        } catch (Exception ignored) {
        }

        if (playerManager != null) {
            playerManager.close();
            playerManager = null;
        }

        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }

        reviveItem = null;

        // Help GC on nonstandard plugin reloaders (e.g., PlugMan)
        instance = null;

        getLogger().info("=======================================");
        getLogger().info("     Disabling LivesSMP v" + version);
        getLogger().info("=======================================");

        DebugLog.memory(this, "onDisable:done");
    }

    public static LivesSMP getInstance() {
        return instance;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ReviveItem getReviveItem() {
        return reviveItem;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
