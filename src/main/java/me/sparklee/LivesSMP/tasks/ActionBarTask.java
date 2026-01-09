package me.sparklee.LivesSMP.tasks;

import me.sparklee.LivesSMP.LivesSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.sparklee.LivesSMP.utils.DebugLog;

public class ActionBarTask implements Runnable {

    private final LivesSMP plugin;

    public ActionBarTask(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("actionbar.enabled", true)) return;

        DebugLog.d(plugin, "ActionBarTask: online=" + Bukkit.getOnlinePlayers().size());

        for (Player player : Bukkit.getOnlinePlayers()) {
            int lives = plugin.getPlayerManager().getLives(player);

            // Display format: ❤x<lives> in red (#FF0000)
            Component message = Component.text("❤ x" + lives)
                    .color(TextColor.color(0xFF0000));

            player.sendActionBar(message);
        }
    }
}
