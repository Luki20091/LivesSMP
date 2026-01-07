package me.sparklee.LivesSMP.commands;

import me.sparklee.LivesSMP.LivesSMP;
import me.sparklee.LivesSMP.items.ReviveItem;
import me.sparklee.LivesSMP.utils.MessageManager;
import me.sparklee.LivesSMP.utils.TeleportUtils;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.BanEntry;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ReviveCommand implements CommandExecutor, TabCompleter {

    private final LivesSMP plugin;

    public ReviveCommand(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageManager.get("only-player", "&cOnly players can use this command!"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(MessageManager.get("revive-usage", "&cUsage: /revive <player>"));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        String displayName = (held.hasItemMeta() && held.getItemMeta() != null) ? held.getItemMeta().getDisplayName() : null;
        if (!plugin.getReviveItem().isReviveCrystal(held) || displayName == null || !displayName.equals(ReviveItem.DISPLAY_NAME)) {
            player.sendMessage(MessageManager.get("revive-invalid-item", "&cYou must hold a Revive Crystal to use this!"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || !target.hasPlayedBefore()) {
            player.sendMessage(MessageManager.get("revive-invalid-player", "&cThat player has never joined the server!"));
            return true;
        }

        //  Get target's current lives (works for both MySQL and YAML)
        int lives;
        if (plugin.getDatabaseManager().isEnabled()) {
            lives = plugin.getDatabaseManager().getLives(target.getUniqueId().toString());
        } else {
            lives = plugin.getPlayerManager().getLives(Bukkit.getOfflinePlayer(target.getUniqueId()));
        }

        // Prevent reviving players who still have lives
        if (lives > 0) {
            player.sendMessage(MessageManager.formatPlaceholders(
                    MessageManager.get("revive-not-zero", "&eThat player still has lives left and cannot be revived!"),
                    player.getName(), target.getName(), lives
            ));
            return true;
        }

        //  Only proceed if player truly has 0 lives
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        if (banList.isBanned(target.getName())) {
            banList.pardon(target.getName());
        }

        int reviveLives = plugin.getConfig().getInt("revive-lives", 1);
        plugin.getPlayerManager().setLives(target.getUniqueId(), reviveLives);
        held.setAmount(held.getAmount() - 1);

        // Mark for sanctuary teleport after revive
        plugin.getPlayerManager().markReviveTeleport(target.getUniqueId());

        // Broadcast
        Bukkit.broadcastMessage(MessageManager.formatPlaceholders(
                MessageManager.get("revive-broadcast", "&#FF9F68⚡ &e%player% &7has revived &b%target% &7using a Revive Crystal!"),
                player.getName(), target.getName(), 0
        ));

        // Sender message
        player.sendMessage(MessageManager.formatPlaceholders(
                MessageManager.get("revive-success", "&aYou revived &e%target%&a!"),
                player.getName(), target.getName(), 0
        ));

        // Target message
        if (target.isOnline()) {
            target.getPlayer().sendMessage(MessageManager.formatPlaceholders(
                    MessageManager.get("revive-target", "&aYou’ve been revived by &e%player%&a!"),
                    player.getName(), target.getName(), 0
            ));

            // If online, teleport immediately to sanctuary and consume the flag
            Bukkit.getScheduler().runTask(plugin, () -> {
                TeleportUtils.teleportToSanctuary(plugin, target.getPlayer());
                plugin.getPlayerManager().consumeReviveTeleport(target.getUniqueId());
            });
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length != 1) {
            return Collections.emptyList();
        }

        String prefix = args[0] == null ? "" : args[0].toLowerCase();
        Set<String> candidates = new HashSet<>();

        // Players with 0 lives from storage (MySQL/YAML)
        for (UUID uuid : plugin.getPlayerManager().getUuidsWithLives(0)) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName();
            if (name != null && !name.isBlank()) {
                candidates.add(name);
            }
        }

        // Also include currently banned names (covers cases where ban exists even if storage is missing)
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        for (Object obj : banList.getBanEntries()) {
            if (obj instanceof BanEntry<?> entry) {
                Object targetObj = entry.getTarget();
                if (targetObj == null) continue;
                String targetName = String.valueOf(targetObj);
                if (!targetName.isBlank() && !"null".equalsIgnoreCase(targetName)) {
                    candidates.add(targetName);
                }
            }
        }

        List<String> result = new ArrayList<>();
        for (String name : candidates) {
            if (prefix.isEmpty() || name.toLowerCase().startsWith(prefix)) {
                result.add(name);
            }
        }

        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
