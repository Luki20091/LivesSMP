package me.sparklee.LivesSMP.commands;

import me.sparklee.LivesSMP.LivesSMP;
import me.sparklee.LivesSMP.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class TopLivesCommand implements CommandExecutor {

    private final LivesSMP plugin;

    public TopLivesCommand(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cUżycie: /" + label + " [strona]");
                return true;
            }
        }

        int pageSize = 10;
        if (plugin.getDatabaseManager().isEnabled()) {
            showMySQLLeaderboard(sender, page, pageSize);
        } else {
            showYAMLLeaderboard(sender, page, pageSize);
        }
        return true;
    }

    private void showMySQLLeaderboard(CommandSender sender, int page, int pageSize) {
        int total = 0;
        try (PreparedStatement countPs = plugin.getDatabaseManager().getConnection().prepareStatement(
            "SELECT COUNT(*) AS total FROM player_lives"
        ); ResultSet crs = countPs.executeQuery()) {
            if (crs.next()) total = crs.getInt("total");
        } catch (Exception e) {
            plugin.getLogger().warning("[LivesSMP] Failed to count leaderboard rows: " + e.getMessage());
        }

        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (page > totalPages) page = totalPages;
        int offset = (page - 1) * pageSize;

        sender.sendMessage(" ");
        sender.sendMessage("§6§lTop graczy po ilości żyć §7- strona §e" + page + "§7/§e" + totalPages);
        sender.sendMessage("§7------------------------------------");
        sender.sendMessage(" ");

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
            "SELECT uuid, lives FROM player_lives ORDER BY lives DESC LIMIT ? OFFSET ?"
        )) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int lives = rs.getInt("lives");

                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    String name = player != null && player.getName() != null ? player.getName() : "Unknown";

                    int rank = offset + (++index);
                    sender.sendMessage("§e#" + rank + " §f" + name + " §7— §c" + lives + " ♥");
                }
            }

        } catch (Exception e) {
            sender.sendMessage(MessageManager.get("leaderboard-error", "&cFailed to load leaderboard! Check console."));
            plugin.getLogger().severe("[LivesSMP] Failed to fetch MySQL leaderboard: " + e.getMessage());
        }

        sender.sendMessage("§7------------------------------------");
        sender.sendMessage(" ");
    }

    private void showYAMLLeaderboard(CommandSender sender, int page, int pageSize) {
        File dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            sender.sendMessage(MessageManager.get("leaderboard-empty", "&7No player data found yet!"));
            return;
        }

        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        Map<String, Integer> livesMap = new HashMap<>();

        if (dataConfig.contains("data")) {
            for (String uuid : dataConfig.getConfigurationSection("data").getKeys(false)) {
                int lives = dataConfig.getInt("data." + uuid, 0);
                livesMap.put(uuid, lives);
            }
        }

        if (livesMap.isEmpty()) {
            sender.sendMessage(MessageManager.get("leaderboard-empty", "&7No player data found yet!"));
            return;
        }

        // Sort descending
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(livesMap.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int total = sorted.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (page > totalPages) page = totalPages;
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(total, from + pageSize);

        sender.sendMessage(" ");
        sender.sendMessage("§6§lTop graczy po ilości żyć §7- strona §e" + page + "§7/§e" + totalPages);
        sender.sendMessage("§7------------------------------------");
        sender.sendMessage(" ");

        int rank = from + 1;
        for (Map.Entry<String, Integer> entry : sorted.subList(from, to)) {
            UUID uuid = UUID.fromString(entry.getKey());
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            String name = player != null && player.getName() != null ? player.getName() : "Unknown";
            int lives = entry.getValue();

            sender.sendMessage("§e#" + rank + " §f" + name + " §7— §c" + lives + " ♥");
            rank++;
        }

        sender.sendMessage("§7------------------------------------");
        sender.sendMessage(" ");
    }
}
