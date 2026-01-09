package me.sparklee.LivesSMP.data;

import me.sparklee.LivesSMP.LivesSMP;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import me.sparklee.LivesSMP.utils.DebugLog;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerManager {

    private final LivesSMP plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    private File reviveTeleportFile;
    private FileConfiguration reviveTeleportConfig;

    public PlayerManager(LivesSMP plugin) {
        this.plugin = plugin;

        // Load or create data.yml only if MySQL is disabled
        if (!plugin.getDatabaseManager().isEnabled()) {
            dataFile = new File(plugin.getDataFolder(), "data.yml");
            if (!dataFile.exists()) {
                try {
                    dataFile.getParentFile().mkdirs();
                    dataFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to create data.yml!");
                }
            }
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        }

        // Always store revive-teleport flags in a small YAML file.
        reviveTeleportFile = new File(plugin.getDataFolder(), "revive-teleport.yml");
        if (!reviveTeleportFile.exists()) {
            try {
                reviveTeleportFile.getParentFile().mkdirs();
                reviveTeleportFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create revive-teleport.yml!");
            }
        }
        reviveTeleportConfig = YamlConfiguration.loadConfiguration(reviveTeleportFile);

        // Best-effort migration from older versions that stored flags in data.yml
        if (dataConfig != null && dataConfig.isConfigurationSection("reviveTeleport")) {
            ConfigurationSection section = dataConfig.getConfigurationSection("reviveTeleport");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (section.getBoolean(key, false)) {
                        // migrate to timestamp format
                        reviveTeleportConfig.set("reviveTeleport." + key, System.currentTimeMillis());
                    }
                }
            }
            dataConfig.set("reviveTeleport", null);
            saveData();
            saveReviveTeleportData();
        }

        // Best-effort cleanup at startup to prevent unbounded growth.
        pruneExpiredReviveTeleports(true);
    }

    private long getReviveTeleportTtlMillis() {
        int days = plugin.getConfig().getInt("revive-teleport.expire-after-days", 30);
        if (days <= 0) return -1L;
        return TimeUnit.DAYS.toMillis(days);
    }

    private boolean isReviveTeleportExpired(long timestampMillis, long nowMillis) {
        long ttl = getReviveTeleportTtlMillis();
        if (ttl <= 0) return false;
        // guard against clock skew / corrupted data
        if (timestampMillis <= 0) return true;
        return nowMillis - timestampMillis > ttl;
    }

    // ==================================================
    //                 CONFIG HELPERS
    // ==================================================

    /**
     * Returns the configured max lives from config.yml
     * If set to -1, it means there is no limit.
     */
    public int getMaxLives() {
        return plugin.getConfig().getInt("max-lives", 10);
    }

    /**
     * Returns true if there is no max-lives limit (-1 in config.yml)
     */
    public boolean isUnlimitedLives() {
        return getMaxLives() == -1;
    }

    /**
     * Returns the default starting lives (configurable)
     */
    public int getDefaultLives() {
        return plugin.getConfig().getInt("starting-lives", 3);
    }

    // ==================================================
    //               GET LIVES METHODS
    // ==================================================

    public int getLives(Player player) {
        return getLives(player.getUniqueId());
    }

    public int getLives(OfflinePlayer offlinePlayer) {
        return getLives(offlinePlayer.getUniqueId());
    }

    public int getLives(UUID uuid) {
        int defaultLives = getDefaultLives();

        // MySQL mode
        if (plugin.getDatabaseManager().isEnabled()) {
            int lives = plugin.getDatabaseManager().getLives(uuid.toString());
            if (lives == -1) {
                setLives(uuid, defaultLives);
                return defaultLives;
            }
            return lives;
        }

        // File mode
        return dataConfig.getInt("data." + uuid, defaultLives);
    }

    // ==================================================
    //               SET LIVES METHODS
    // ==================================================

    public void setLives(Player player, int lives) {
        setLives(player.getUniqueId(), lives);
    }

    public void setLives(UUID uuid, int lives) {
        DebugLog.d(plugin, "PlayerManager.setLives: uuid=" + uuid + " lives=" + lives + " mysql=" + plugin.getDatabaseManager().isEnabled());
        // MySQL mode
        if (plugin.getDatabaseManager().isEnabled()) {
            plugin.getDatabaseManager().setLives(uuid.toString(), lives);
            return;
        }

        // File mode
        dataConfig.set("data." + uuid, lives);
        saveData();
    }

    // ==================================================
    //      POST-REVIVE TELEPORT FLAG (ONE-TIME)
    // ==================================================

    /**
     * Mark a player to be teleported to sanctuary once (e.g., after revive).
     */
    public void markReviveTeleport(UUID uuid) {
        DebugLog.d(plugin, "PlayerManager.markReviveTeleport: uuid=" + uuid);
        reviveTeleportConfig.set("reviveTeleport." + uuid, System.currentTimeMillis());
        saveReviveTeleportData();
    }

    /**
     * Consume and clear the one-time teleport flag. Returns true if it was set.
     */
    public boolean consumeReviveTeleport(UUID uuid) {
        String path = "reviveTeleport." + uuid;

        Object raw = reviveTeleportConfig.get(path);
        if (raw == null) return false;

        long now = System.currentTimeMillis();
        long ts;

        // Backward-compat: older versions stored boolean "true".
        if (raw instanceof Boolean) {
            if (!((Boolean) raw)) {
                reviveTeleportConfig.set(path, null);
                saveReviveTeleportData();
                return false;
            }
            ts = now;
        } else {
            ts = reviveTeleportConfig.getLong(path, 0L);
        }

        if (isReviveTeleportExpired(ts, now)) {
            reviveTeleportConfig.set(path, null);
            saveReviveTeleportData();
            DebugLog.d(plugin, "PlayerManager.consumeReviveTeleport: uuid=" + uuid + " -> expired");
            return false;
        }

        DebugLog.d(plugin, "PlayerManager.consumeReviveTeleport: uuid=" + uuid + " -> true");
        reviveTeleportConfig.set(path, null);
        saveReviveTeleportData();
        return true;
    }

    public int getPendingReviveTeleportsCount() {
        ConfigurationSection section = reviveTeleportConfig.getConfigurationSection("reviveTeleport");
        if (section == null) return 0;

        long now = System.currentTimeMillis();
        int count = 0;
        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            if (raw == null) continue;

            long ts;
            if (raw instanceof Boolean) {
                if (!((Boolean) raw)) continue;
                ts = now;
            } else {
                ts = section.getLong(key, 0L);
            }

            if (!isReviveTeleportExpired(ts, now)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Removes expired revive-teleport flags. Returns how many entries were removed.
     */
    public int pruneExpiredReviveTeleports(boolean saveIfChanged) {
        long ttl = getReviveTeleportTtlMillis();
        if (ttl <= 0) return 0;

        ConfigurationSection section = reviveTeleportConfig.getConfigurationSection("reviveTeleport");
        if (section == null) return 0;

        long now = System.currentTimeMillis();
        int removed = 0;

        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            if (raw == null) continue;

            long ts;
            if (raw instanceof Boolean) {
                // old format: treat as "recent" so it doesn't get wiped immediately; it will only persist until consumed.
                if (!((Boolean) raw)) {
                    section.set(key, null);
                    removed++;
                    continue;
                }
                ts = now;
            } else {
                ts = section.getLong(key, 0L);
            }

            if (isReviveTeleportExpired(ts, now)) {
                section.set(key, null);
                removed++;
            }
        }

        if (removed > 0 && saveIfChanged) {
            saveReviveTeleportData();
        }

        return removed;
    }

    // ==================================================
    //               MODIFY LIVES METHODS
    // ==================================================

    /**
     * Increases a player's lives safely, respecting the max-lives limit (unless unlimited)
     */
    public int addLives(UUID uuid, int amount) {
        DebugLog.d(plugin, "PlayerManager.addLives: uuid=" + uuid + " amount=" + amount);
        int current = getLives(uuid);
        int max = getMaxLives();

        if (!isUnlimitedLives() && current + amount > max) {
            amount = Math.max(0, max - current);
        }

        int newLives = isUnlimitedLives() ? current + amount : Math.min(current + amount, max);
        setLives(uuid, newLives);
        DebugLog.d(plugin, "PlayerManager.addLives: current=" + current + " newLives=" + newLives + " max=" + max);
        return newLives;
    }

    /**
     * Decreases a player's lives, never below 0
     */
    public int removeLives(UUID uuid, int amount) {
        DebugLog.d(plugin, "PlayerManager.removeLives: uuid=" + uuid + " amount=" + amount);
        int current = getLives(uuid);
        int newLives = Math.max(0, current - amount);
        setLives(uuid, newLives);
        DebugLog.d(plugin, "PlayerManager.removeLives: current=" + current + " newLives=" + newLives);
        return newLives;
    }

    /**
     * Decrements 1 life (used on player death)
     */
    public int decrementLife(Player player) {
        int lives = getLives(player) - 1;
        if (lives < 0) lives = 0;
        setLives(player, lives);
        return lives;
    }

    // ==================================================
    //                 HAS DATA CHECK
    // ==================================================

    public boolean hasData(Player player) {
        return hasData(player.getUniqueId());
    }

    public boolean hasData(UUID uuid) {
        if (plugin.getDatabaseManager().isEnabled()) {
            return plugin.getDatabaseManager().getLives(uuid.toString()) != -1;
        }
        return dataConfig.contains("data." + uuid);
    }

    public Set<UUID> getUuidsWithLives(int lives) {
        // MySQL mode
        if (plugin.getDatabaseManager().isEnabled()) {
            return plugin.getDatabaseManager().getUuidsWithLives(lives);
        }

        // File mode
        Set<UUID> result = new HashSet<>();
        if (dataConfig == null) return result;

        ConfigurationSection section = dataConfig.getConfigurationSection("data");
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (section.getInt(key) == lives) {
                    result.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUID keys
            }
        }

        return result;
    }

    // ==================================================
    //                   SAVE HANDLER
    // ==================================================

    public void saveData() {
        if (plugin.getDatabaseManager().isEnabled()) return;
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml!");
        }
    }

    private void saveReviveTeleportData() {
        try {
            reviveTeleportConfig.save(reviveTeleportFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save revive-teleport.yml!");
        }
    }

    public void close() {
        // Best-effort persistence and release of references (helps GC on reloaders).
        saveData();
        saveReviveTeleportData();
        dataConfig = null;
        reviveTeleportConfig = null;
        dataFile = null;
        reviveTeleportFile = null;
    }
}
