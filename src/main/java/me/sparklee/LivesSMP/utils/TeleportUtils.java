package me.sparklee.LivesSMP.utils;

import me.sparklee.LivesSMP.LivesSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.sparklee.LivesSMP.utils.DebugLog;

public class TeleportUtils {

    public static void teleportToSanctuary(LivesSMP plugin, Player player) {
        boolean enabled = plugin.getConfig().getBoolean("death-teleport.enabled", true);
        if (!enabled) {
            DebugLog.d(plugin, "TeleportUtils: teleport disabled, skipping for player=" + player.getName());
            return;
        }

        String worldName = plugin.getConfig().getString("death-teleport.world", "world");
        double x = plugin.getConfig().getDouble("death-teleport.x", -0.5);
        double y = plugin.getConfig().getDouble("death-teleport.y", 70.0);
        double z = plugin.getConfig().getDouble("death-teleport.z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble("death-teleport.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("death-teleport.pitch", 0.0);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[LivesSMP] death-teleport.world is invalid: " + worldName);
            DebugLog.d(plugin, "TeleportUtils: invalid worldName=" + worldName + " for player=" + player.getName());
            return;
        }

        Location location = new Location(world, x, y, z, yaw, pitch);
        DebugLog.d(plugin, "TeleportUtils: teleporting player=" + player.getName()
                + " to " + worldName + " (" + x + "," + y + "," + z + ") yaw=" + yaw + " pitch=" + pitch);
        player.teleport(location);
    }
}
