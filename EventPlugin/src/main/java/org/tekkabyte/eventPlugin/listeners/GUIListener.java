package org.tekkabyte.eventPlugin.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.gui.CraftingGUI;
import org.tekkabyte.eventPlugin.managers.RecipeManager;
import org.tekkabyte.eventPlugin.models.CustomRecipe;

import java.util.Map;

public class GUIListener implements Listener {

    private final EventPlugin plugin;
    private final RecipeManager recipeManager;
    private final CraftingGUI craftingGUI;

    public GUIListener(EventPlugin plugin, RecipeManager recipeManager, CraftingGUI craftingGUI) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.craftingGUI = craftingGUI;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (isOurGui(e.getView().getTitle())) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!isOurGui(e.getView().getTitle())) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(craftingGUI.getKeyAction(), PersistentDataType.STRING);
        if (action != null) {
            handleAction(player, action, meta);
            return;
        }

        String recipeId = meta.getPersistentDataContainer().get(craftingGUI.getKeyRecipeId(), PersistentDataType.STRING);
        if (recipeId != null) {
            CustomRecipe recipe = recipeManager.getRecipe(recipeId);
            if (recipe == null) {
                player.sendMessage(ChatColor.RED + "That recipe no longer exists.");
                player.closeInventory();
                return;
            }
            craftingGUI.openRecipeDetails(player, recipe);
        }
    }

    private void handleAction(Player player, String action, ItemMeta meta) {
        if (CraftingGUI.ACTION_BACK.equalsIgnoreCase(action)) {
            craftingGUI.openCraftingMenu(player);
            return;
        }

        if (CraftingGUI.ACTION_CRAFT.equalsIgnoreCase(action)) {
            String recipeId = meta.getPersistentDataContainer().get(craftingGUI.getKeyRecipeId(), PersistentDataType.STRING);
            if (recipeId == null) return;

            CustomRecipe recipe = recipeManager.getRecipe(recipeId);
            if (recipe == null) {
                player.sendMessage(ChatColor.RED + "That recipe no longer exists.");
                player.closeInventory();
                return;
            }

            if (!recipe.canCraftMore()) {
                player.sendMessage(ChatColor.RED + "That recipe is out of crafts.");
                craftingGUI.openRecipeDetails(player, recipe);
                return;
            }

            boolean success = recipeManager.tryCraftAndConsume(player, player.getInventory().getContents(), recipe);
            if (!success) {
                player.sendMessage(ChatColor.RED + "You don't have enough materials.");
                craftingGUI.openRecipeDetails(player, recipe);
                return;
            }

            ItemStack result = recipe.getResult().clone();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                player.sendMessage(ChatColor.YELLOW + "Your inventory was full, so the item was dropped on the ground.");
            }

            player.updateInventory();

            craftingGUI.openRecipeDetails(player, recipe);
        }
    }

    private boolean isOurGui(String title) {
        if (title == null) return false;

        String rawMain = plugin.getConfig().getString("settings.gui-title", "Custom Crafting");
        String coloredMain = ChatColor.translateAlternateColorCodes('&', rawMain);
        String mainTitle = ChatColor.DARK_PURPLE + coloredMain;

        String t = strip(title);
        if (t.equalsIgnoreCase(strip(mainTitle))) return true;

        return t.toLowerCase().startsWith("craft: ");
    }

    private String strip(String s) {
        return ChatColor.stripColor(s == null ? "" : s);
    }
}