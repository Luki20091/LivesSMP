package me.sparklee.LivesSMP.events;

import me.sparklee.LivesSMP.LivesSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RespawnTeleportListener implements Listener {

    private final LivesSMP plugin;

    public RespawnTeleportListener(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("livessmp.bypass")) return;

        boolean enabled = plugin.getConfig().getBoolean("death-teleport.enabled", true);
        if (!enabled) return;

        String worldName = plugin.getConfig().getString("death-teleport.world", "world");
        double x = plugin.getConfig().getDouble("death-teleport.x", -0.5);
        double y = plugin.getConfig().getDouble("death-teleport.y", 70.0);
        double z = plugin.getConfig().getDouble("death-teleport.z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble("death-teleport.yaw", 90.92);
        float pitch = (float) plugin.getConfig().getDouble("death-teleport.pitch", 2.59);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[LivesSMP] death-teleport.world is invalid: " + worldName);
            return;
        }

        Location location = new Location(world, x, y, z, yaw, pitch);

        // Run one tick later so the player is fully spawned and moved away from danger.
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(location), 1L);
    }
}
