package me.sparklee.LivesSMP.events;

import me.sparklee.LivesSMP.LivesSMP;
import me.sparklee.LivesSMP.utils.MessageManager;
import me.sparklee.LivesSMP.utils.TeleportUtils;
import me.sparklee.LivesSMP.utils.DebugLog;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final LivesSMP plugin;

    public JoinListener(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        DebugLog.d(plugin, "JoinListener: player=" + player.getName() + " uuid=" + player.getUniqueId());

        int startingLives = plugin.getConfig().getInt("starting-lives", 3);
        int postBanLives = plugin.getConfig().getInt("post-ban-lives", startingLives);
        int lives = plugin.getPlayerManager().getLives(player);

        DebugLog.d(plugin, "JoinListener: startingLives=" + startingLives + " postBanLives=" + postBanLives + " storedLives=" + lives);

        // If marked due to offline revive, teleport to sanctuary once
        if (plugin.getPlayerManager().consumeReviveTeleport(player.getUniqueId())) {
            DebugLog.d(plugin, "JoinListener: consumeReviveTeleport=true -> teleport to sanctuary");
            Bukkit.getScheduler().runTask(plugin, () -> TeleportUtils.teleportToSanctuary(plugin, player));
        }

        // If the player has no data yet
        if (!plugin.getPlayerManager().hasData(player)) {
            DebugLog.d(plugin, "JoinListener: no data -> setLives=" + startingLives);
            plugin.getPlayerManager().setLives(player, startingLives);

            player.sendMessage(MessageManager.formatPlaceholders(
                    MessageManager.get("join-new", "&aWelcome to Lives SMP! You have &e%lives% &alives."),
                    player.getName(), null, startingLives
            ));
            return;
        }

        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        if (lives <= 0 && !banList.isBanned(player.getName())) {
            DebugLog.d(plugin, "JoinListener: lives<=0 but not banned -> auto restore to " + postBanLives);
            plugin.getPlayerManager().setLives(player, postBanLives);
            player.sendMessage(MessageManager.formatPlaceholders(
                MessageManager.get("auto-revive", "&aYou were revived and restored to &e%lives% &alives!"),
                player.getName(), null, postBanLives
            ));
            plugin.getLogger().info("[LivesSMP] Auto-restored " + player.getName() + " to " + postBanLives + " lives after unban.");

            // Teleport to sanctuary after auto-revive
            Bukkit.getScheduler().runTask(plugin, () -> TeleportUtils.teleportToSanctuary(plugin, player));
            return;
        }

        // Normal join message
        player.sendMessage(MessageManager.formatPlaceholders(
                MessageManager.get("join-return", "&7Welcome back! You currently have &e%lives% &7lives."),
                player.getName(), null, lives
        ));

        DebugLog.memory(plugin, "after-join:" + player.getName());
    }
}
