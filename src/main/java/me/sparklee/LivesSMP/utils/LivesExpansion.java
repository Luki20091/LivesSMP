package me.sparklee.LivesSMP.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.sparklee.LivesSMP.LivesSMP;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LivesExpansion extends PlaceholderExpansion {

    private final LivesSMP plugin;

    public LivesExpansion(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "livessmp";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Sparklee";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // This expansion is bundled with the plugin. Persisting across /reload (or external reloaders)
        // while holding a plugin reference is a common source of classloader memory leaks.
        return false;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";

        // %livessmp_lives%
        if (identifier.equalsIgnoreCase("lives")) {
            return String.valueOf(plugin.getPlayerManager().getLives(player));
        }

        // %livessmp_status%
        if (identifier.equalsIgnoreCase("status")) {
            int lives = plugin.getPlayerManager().getLives(player);
            if (lives > 2) return "§aHealthy";
            if (lives == 2) return "§eCautious";
            if (lives == 1) return "§cCritical";
            return "§7Eliminated";
        }

        return null;
    }
}
