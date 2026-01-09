package me.sparklee.LivesSMP.events;

import me.sparklee.LivesSMP.LivesSMP;
import me.sparklee.LivesSMP.utils.MessageManager;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import org.bukkit.event.EventPriority;

import me.sparklee.LivesSMP.utils.DebugLog;

import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeathListener implements Listener {

    private final LivesSMP plugin;

    public DeathListener(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // If another plugin already cancelled/handled, do nothing
        if (event.isCancelled()) return;

        // Skip if player has bypass permission
        if (player.hasPermission("livessmp.bypass")) return;

        DebugLog.d(plugin, "DeathListener: player=" + player.getName() + " uuid=" + player.getUniqueId());

        int amountToLose = 1;
        Player killer = player.getKiller();
        boolean lifeStealEnabled = killer != null && plugin.getConfig().getBoolean("life-steal.enabled", true);
        if (lifeStealEnabled) {
            amountToLose = Math.max(1, plugin.getConfig().getInt("life-steal.amount", 1));
        }

        int beforeLives = plugin.getPlayerManager().getLives(player);
        DebugLog.d(plugin, "DeathListener: killer=" + (killer == null ? "null" : killer.getName())
            + " lifeStealEnabled=" + lifeStealEnabled
            + " amountToLose=" + amountToLose
            + " victimLives(before)=" + beforeLives);

        int lives = plugin.getPlayerManager().removeLives(player.getUniqueId(), amountToLose);
        DebugLog.d(plugin, "DeathListener: victimLives(after)=" + lives);

        // Life-steal: killer gains the same amount (if victim isn't bypass)
        if (lifeStealEnabled && !killer.hasPermission("livessmp.bypass")) {
            int killerLives = plugin.getPlayerManager().addLives(killer.getUniqueId(), amountToLose);

            DebugLog.d(plugin, "DeathListener: killerLives(after)=" + killerLives);

            killer.sendMessage(MessageManager.formatPlaceholders(
                MessageManager.get("life-steal-gain", "&aYou stole &e%amount% &alife(s) from &e%target%! &7(You now have &e%lives%&7)"),
                killer.getName(), player.getName(), killerLives
            ).replace("%amount%", String.valueOf(amountToLose)));

            player.sendMessage(MessageManager.formatPlaceholders(
                MessageManager.get("life-steal-loss", "&cYou lost &e%amount% &clife(s) to &e%player%! &7(You now have &e%lives%&7)"),
                killer.getName(), player.getName(), lives
            ).replace("%amount%", String.valueOf(amountToLose)));

            if (plugin.getConfig().getBoolean("life-steal.broadcast", true)) {
            Bukkit.broadcastMessage(MessageManager.formatPlaceholders(
                MessageManager.get("life-steal-broadcast", "&#FF9F68⚔ &e%player% &7stole &c%amount% &7life(s) from &e%target%!"),
                killer.getName(), player.getName(), 0
            ).replace("%amount%", String.valueOf(amountToLose)));
            }
        }

        // Player lost all lives
        if (lives <= 0) {
            boolean tempBanEnabled = plugin.getConfig().getBoolean("temporary-ban.enabled", false);
            String durationStr = plugin.getConfig().getString("temporary-ban.duration", "1h");
            Date expires = tempBanEnabled ? parseDuration(durationStr) : null;

            DebugLog.d(plugin, "DeathListener: lives<=0 -> ban temp=" + tempBanEnabled + " duration=" + durationStr + " expires=" + expires);

            String banReason = MessageManager.get("no-lives-left", "&c☠ You’ve lost all your lives!");
            String kickMessage;

            if (tempBanEnabled) {
                kickMessage = MessageManager.get("ban-temp-message",
                        "&c☠ You’ve lost all your lives!\n&7You are banned for a limited time.");
            } else {
                kickMessage = MessageManager.get("ban-permanent-message",
                        "&c☠ You’ve lost all 3 of your lives!\n&7You are now banned until someone revives you.");
            }

            // Delay ban + kick by 1 tick so other plugins (e.g., Graves) can finish processing the death event.
            Bukkit.getScheduler().runTask(plugin, () -> {
                BanList banList = Bukkit.getBanList(BanList.Type.NAME);
                banList.addBan(player.getName(), banReason, expires, "LivesSMP");
                player.kickPlayer(kickMessage);
            });

            plugin.getLogger().info(player.getName() + " was "
                    + (tempBanEnabled ? "temporarily" : "permanently")
                    + " banned for losing all lives.");

            DebugLog.memory(plugin, "after-ban:" + player.getName());
        } else {
            // Player still has lives remaining
            if (!lifeStealEnabled) {
                player.sendMessage(MessageManager.formatPlaceholders(
                        MessageManager.get("life-lost", "&cYou lost a life! &7Lives remaining: &e%lives%"),
                        player.getName(), null, lives
                ));
            }
        }
    }

    /**
     * Parses duration strings like "30m", "2h", "1d" into a Date.
     */
    private Date parseDuration(String input) {
        Pattern pattern = Pattern.compile("(\\d+)([mhd])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);

        if (!matcher.matches()) {
            plugin.getLogger().warning("[LivesSMP] Invalid temporary-ban duration format: " + input);
            return null;
        }

        int value = Integer.parseInt(matcher.group(1));
        char unit = matcher.group(2).toLowerCase().charAt(0);

        Calendar calendar = Calendar.getInstance();
        switch (unit) {
            case 'm' -> calendar.add(Calendar.MINUTE, value);
            case 'h' -> calendar.add(Calendar.HOUR, value);
            case 'd' -> calendar.add(Calendar.DAY_OF_MONTH, value);
        }

        return calendar.getTime();
    }
}
