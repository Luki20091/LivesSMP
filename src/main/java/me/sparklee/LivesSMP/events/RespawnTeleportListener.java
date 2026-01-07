package me.sparklee.LivesSMP.events;

import me.sparklee.LivesSMP.LivesSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import me.sparklee.LivesSMP.utils.TeleportUtils;

public class RespawnTeleportListener implements Listener {

    private final LivesSMP plugin;

    public RespawnTeleportListener(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("livessmp.bypass")) return;

        // Only teleport on respawn if a one-time post-revive flag is set.
        // Normal deaths should use bed/anchor/spawn as usual.
        if (plugin.getPlayerManager().consumeReviveTeleport(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> TeleportUtils.teleportToSanctuary(plugin, player), 1L);
        }
    }
}
