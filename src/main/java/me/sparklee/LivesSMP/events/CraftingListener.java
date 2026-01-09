package me.sparklee.LivesSMP.events;

import me.sparklee.LivesSMP.LivesSMP;
import me.sparklee.LivesSMP.utils.MessageManager;
import me.sparklee.LivesSMP.utils.DebugLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {

    private final LivesSMP plugin;
    private static final String PERMISSION_CRAFT_REVIVE_CRYSTAL = "livessmp.craft.revivecrystal";

    public CraftingListener(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        // Block crafting Revive Crystal unless player has permission
        ItemStack result = event.getInventory().getResult();
        if (result != null && plugin.getReviveItem().isReviveCrystal(result)) {
            if (!event.getViewers().isEmpty() && event.getViewers().get(0) instanceof Player player) {
                if (!player.hasPermission(PERMISSION_CRAFT_REVIVE_CRYSTAL)) {
                    DebugLog.d(plugin, "CraftingListener: blocked revive crystal craft for player=" + player.getName());
                    event.getInventory().setResult(null);
                }
            }
            return;
        }

        // Prevent using a Revive Crystal as an ingredient in any recipe
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (plugin.getReviveItem().isReviveCrystal(item)) {
                DebugLog.d(plugin, "CraftingListener: blocked recipe using revive crystal as ingredient");
                // Cancel the recipe output
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        if (result == null) return;

        if (plugin.getReviveItem().isReviveCrystal(result)
                && !player.hasPermission(PERMISSION_CRAFT_REVIVE_CRYSTAL)) {
            DebugLog.d(plugin, "CraftingListener: cancelled revive crystal craft (no perm) for player=" + player.getName());
            event.setCancelled(true);
            player.sendMessage(MessageManager.get("no-permission", "&cYou don't have permission to do that!"));
            player.updateInventory();
        }
    }
}
