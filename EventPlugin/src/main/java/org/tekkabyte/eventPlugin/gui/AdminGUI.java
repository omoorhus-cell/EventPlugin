package org.tekkabyte.eventPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.managers.RecipeManager;
import org.tekkabyte.eventPlugin.models.CustomRecipe;

import java.util.ArrayList;
import java.util.List;

public class AdminGUI {

    private final EventPlugin plugin;
    private final RecipeManager recipeManager;

    public static final int SLOT_BACK = 45;
    public static final int SLOT_RESULT = 49;
    public static final int SLOT_SAVE = 52;
    public static final int SLOT_DELETE = 53;

    public static final String ACTION_OPEN = "open";
    public static final String ACTION_BACK = "back";
    public static final String ACTION_SAVE = "save";
    public static final String ACTION_DELETE = "delete";

    private final NamespacedKey keyAction;
    private final NamespacedKey keyRecipeId;

    public AdminGUI(EventPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.keyAction = new NamespacedKey(plugin, "admin_action");
        this.keyRecipeId = new NamespacedKey(plugin, "admin_recipe_id");
    }

    public NamespacedKey getKeyAction() {
        return keyAction;
    }

    public NamespacedKey getKeyRecipeId() {
        return keyRecipeId;
    }

    public String getAdminMenuTitle() {
        String raw = plugin.getConfig().getString("settings.admin-gui-title", "Recipe Editor");
        return clampTitle(ChatColor.DARK_RED + ChatColor.translateAlternateColorCodes('&', raw), 32);
    }

    public String getEditorTitle(CustomRecipe recipe) {
        return clampTitle(ChatColor.DARK_PURPLE + "Edit: " + recipe.getName(), 32);
    }

    public void openAdminMenu(Player player) {
        List<CustomRecipe> recipes = recipeManager.getAllRecipes();
        int size = Math.min(54, Math.max(9, ((recipes.size() + 8) / 9) * 9));

        Inventory inv = Bukkit.createInventory(null, size, getAdminMenuTitle());

        for (int i = 0; i < recipes.size() && i < size; i++) {
            CustomRecipe r = recipes.get(i);

            ItemStack icon = (r.getResult() == null || r.getResult().getType().isAir())
                    ? new ItemStack(Material.BARRIER)
                    : r.getResult().clone();

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + r.getName());

                meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, ACTION_OPEN);
                meta.getPersistentDataContainer().set(keyRecipeId, PersistentDataType.STRING, r.getId());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Click to edit");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }

            inv.setItem(i, icon);
        }

        player.openInventory(inv);
    }

    public void openRecipeEditor(Player player, CustomRecipe recipe) {
        Inventory inv = Bukkit.createInventory(null, 54, getEditorTitle(recipe));

        ItemStack border = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        for (int i = 10; i <= 34; i++) inv.setItem(i, null);

        int slot = 10;
        for (ItemStack mat : recipe.getMaterials()) {
            if (mat == null || mat.getType().isAir()) continue;
            if (slot > 34) break;
            inv.setItem(slot++, mat.clone());
        }

        if (recipe.getResult() != null && !recipe.getResult().getType().isAir()) {
            inv.setItem(SLOT_RESULT, recipe.getResult().clone());
        } else {
            inv.setItem(SLOT_RESULT, pane(Material.BARRIER, ChatColor.RED + "Set Result"));
        }

        inv.setItem(4, pane(Material.BOOK, ChatColor.AQUA + "Editor Info",
                ChatColor.GRAY + "Slots 10-34 = ingredients",
                ChatColor.GRAY + "Slot 49 = result",
                ChatColor.GREEN + "Click SAVE to apply"));

        inv.setItem(SLOT_BACK, actionItem(Material.ARROW, ChatColor.YELLOW + "Back", ACTION_BACK, recipe.getId()));
        inv.setItem(SLOT_SAVE, actionItem(Material.LIME_CONCRETE, ChatColor.GREEN + "SAVE", ACTION_SAVE, recipe.getId(),
                ChatColor.GRAY + "Save changes"));
        inv.setItem(SLOT_DELETE, actionItem(Material.BARRIER, ChatColor.RED + "Delete", ACTION_DELETE, recipe.getId()));

        player.openInventory(inv);
    }

    private ItemStack actionItem(Material mat, String name, String action, String recipeId, String... loreLines) {
        ItemStack is = pane(mat, name, loreLines);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
            if (recipeId != null) {
                meta.getPersistentDataContainer().set(keyRecipeId, PersistentDataType.STRING, recipeId);
            }
            is.setItemMeta(meta);
        }
        return is;
    }

    private ItemStack pane(Material mat, String name, String... loreLines) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines != null && loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String s : loreLines) lore.add(s);
                meta.setLore(lore);
            }
            is.setItemMeta(meta);
        }
        return is;
    }

    private String clampTitle(String title, int maxLen) {
        if (title == null) return "";
        return title.length() <= maxLen ? title : title.substring(0, maxLen);
    }
}