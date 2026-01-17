package org.tekkabyte.eventPlugin.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.gui.AdminGUI;
import org.tekkabyte.eventPlugin.managers.RecipeManager;
import org.tekkabyte.eventPlugin.models.CustomRecipe;

import java.util.ArrayList;
import java.util.List;

public class AdminGUIListener implements Listener {

    private final EventPlugin plugin;
    private final RecipeManager recipeManager;
    private final AdminGUI adminGUI;

    public AdminGUIListener(EventPlugin plugin, RecipeManager recipeManager, AdminGUI adminGUI) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.adminGUI = adminGUI;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();
        boolean isAdminMenu = isAdminMenuTitle(title);
        boolean isEditor = isEditorTitle(title);

        if (!isAdminMenu && !isEditor) return;

        e.setCancelled(true);

        if (e.isShiftClick()) return;

        switch (e.getAction()) {
            case COLLECT_TO_CURSOR:
            case MOVE_TO_OTHER_INVENTORY:
            case HOTBAR_SWAP:
                return;
            default:
                break;
        }

        Inventory top = e.getView().getTopInventory();
        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null) return;

        boolean clickedTop = clickedInv.equals(top);

        if (isAdminMenu) {
            if (!clickedTop) return;

            ItemStack current = e.getCurrentItem();
            if (current == null || current.getType() == Material.AIR) return;

            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            String action = meta.getPersistentDataContainer().get(adminGUI.getKeyAction(), PersistentDataType.STRING);
            String recipeId = meta.getPersistentDataContainer().get(adminGUI.getKeyRecipeId(), PersistentDataType.STRING);

            if (!AdminGUI.ACTION_OPEN.equalsIgnoreCase(action) || recipeId == null) return;

            CustomRecipe recipe = recipeManager.getRecipe(recipeId);
            if (recipe == null) {
                player.sendMessage(ChatColor.RED + "That recipe no longer exists.");
                player.closeInventory();
                return;
            }

            adminGUI.openRecipeEditor(player, recipe);
            return;
        }

        if (isEditor) {
            int slot = e.getRawSlot();
            boolean inTop = slot >= 0 && slot < top.getSize();

            if (!inTop) {
                e.setCancelled(false);
                return;
            }

            if (slot == AdminGUI.SLOT_BACK || slot == AdminGUI.SLOT_SAVE || slot == AdminGUI.SLOT_DELETE || slot == 4) {
                handleEditorButtonClick(player, e.getCurrentItem());
                return;
            }

            if (slot >= 10 && slot <= 34) {
                e.setCancelled(false);
                return;
            }

            if (slot == AdminGUI.SLOT_RESULT) {
                e.setCancelled(false);
                return;
            }

        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String title = e.getView().getTitle();
        boolean isAdminMenu = isAdminMenuTitle(title);
        boolean isEditor = isEditorTitle(title);

        if (!isAdminMenu && !isEditor) return;

        e.setCancelled(true);

        if (!isEditor) return;

        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= e.getView().getTopInventory().getSize()) {
                continue;
            }

            boolean ok = (rawSlot >= 10 && rawSlot <= 34) || (rawSlot == AdminGUI.SLOT_RESULT);
            if (!ok) return;
        }

        e.setCancelled(false);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
    }

    private void handleEditorButtonClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(adminGUI.getKeyAction(), PersistentDataType.STRING);
        String recipeId = meta.getPersistentDataContainer().get(adminGUI.getKeyRecipeId(), PersistentDataType.STRING);

        if (action == null) return;

        if (AdminGUI.ACTION_BACK.equalsIgnoreCase(action)) {
            adminGUI.openAdminMenu(player);
            return;
        }

        if (recipeId == null) return;

        CustomRecipe recipe = recipeManager.getRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage(ChatColor.RED + "That recipe no longer exists.");
            player.closeInventory();
            return;
        }

        if (AdminGUI.ACTION_DELETE.equalsIgnoreCase(action)) {
            recipeManager.deleteRecipe(recipeId);
            player.sendMessage(ChatColor.GREEN + "Deleted recipe: " + recipe.getName());
            adminGUI.openAdminMenu(player);
            return;
        }

        if (AdminGUI.ACTION_SAVE.equalsIgnoreCase(action)) {
            Inventory top = player.getOpenInventory().getTopInventory();

            List<ItemStack> mats = new ArrayList<>();
            for (int i = 10; i <= 34; i++) {
                ItemStack it = top.getItem(i);
                if (it == null || it.getType().isAir()) continue;
                mats.add(it.clone());
            }

            ItemStack result = top.getItem(AdminGUI.SLOT_RESULT);
            if (result == null || result.getType().isAir() || result.getType() == Material.BARRIER) {
                player.sendMessage(ChatColor.RED + "Set a valid result item in slot 49 first.");
                return;
            }

            CustomRecipe updated = new CustomRecipe(
                    recipe.getId(),
                    recipe.getName(),
                    result.clone(),
                    mats,
                    recipe.getMaxCrafts(),
                    recipe.getCraftsUsed()
            );

            recipeManager.saveRecipe(updated);
            player.sendMessage(ChatColor.GREEN + "Saved recipe: " + updated.getName());
            adminGUI.openRecipeEditor(player, updated);
        }
    }

    private boolean isAdminMenuTitle(String title) {
        if (title == null) return false;
        return ChatColor.stripColor(title).equalsIgnoreCase(ChatColor.stripColor(adminGUI.getAdminMenuTitle()));
    }

    private boolean isEditorTitle(String title) {
        if (title == null) return false;
        return ChatColor.stripColor(title).toLowerCase().startsWith("edit: ");
    }
}