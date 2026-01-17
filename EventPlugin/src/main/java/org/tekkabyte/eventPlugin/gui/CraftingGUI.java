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

public class CraftingGUI {

    public static final String ACTION_CRAFT = "craft";
    public static final String ACTION_BACK = "back";

    private final EventPlugin plugin;
    private final RecipeManager recipeManager;

    private final NamespacedKey keyRecipeId;
    private final NamespacedKey keyAction;

    public CraftingGUI(EventPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.keyRecipeId = new NamespacedKey(plugin, "recipe_id");
        this.keyAction = new NamespacedKey(plugin, "action");
    }

    public NamespacedKey getKeyRecipeId() {
        return keyRecipeId;
    }

    public NamespacedKey getKeyAction() {
        return keyAction;
    }

    public void openCraftingMenu(Player player) {
        List<CustomRecipe> recipes = recipeManager.getAllRecipes();
        int size = Math.min(54, ((recipes.size() + 8) / 9) * 9);
        if (size < 9) size = 9;

        String rawTitle = plugin.getConfig().getString("settings.gui-title", "Custom Crafting");
        String coloredTitle = ChatColor.translateAlternateColorCodes('&', rawTitle);
        String title = clampTitle(ChatColor.DARK_PURPLE + coloredTitle, 32);

        Inventory inv = Bukkit.createInventory(null, size, title);

        for (int i = 0; i < recipes.size() && i < size; i++) {
            CustomRecipe recipe = recipes.get(i);
            if (recipe == null || recipe.getResult() == null) continue;

            ItemStack displayItem = recipe.getResult().clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta == null) continue;

            if (!meta.hasDisplayName()) {
                meta.setDisplayName(ChatColor.GREEN + recipe.getName());
            }

            meta.getPersistentDataContainer().set(keyRecipeId, PersistentDataType.STRING, recipe.getId());

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            if (!lore.isEmpty()) lore.add("");

            if (!recipe.canCraftMore()) {
                lore.add(ChatColor.RED + "✗ Out of crafts");
                lore.add(ChatColor.GREEN + "Click to view recipe");
            } else {
                boolean canCraft = recipeManager.canCraft(player.getInventory().getContents(), recipe);
                if (canCraft) {
                    lore.add(ChatColor.GREEN + "✓ You can craft this!");
                    lore.add(ChatColor.GRAY + "Click to view & craft");
                } else {
                    lore.add(ChatColor.GREEN + "Click to view recipe");
                }
            }

            lore.add("");

            if (recipe.isUnlimited()) {
                lore.add(ChatColor.AQUA + "Craft Limit: " + ChatColor.WHITE + "Unlimited");
            } else {
                lore.add(ChatColor.AQUA + "Crafts Left: " + ChatColor.WHITE + recipe.remainingCrafts());
            }

            meta.setLore(lore);
            displayItem.setItemMeta(meta);

            inv.setItem(i, displayItem);
        }

        player.openInventory(inv);
    }

    public void openRecipeDetails(Player player, CustomRecipe recipe) {
        String title = clampTitle(ChatColor.DARK_GREEN + "Craft: " + recipe.getName(), 32);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        inv.setItem(4, createInfoItem(recipe));

        int slot = 19;
        if (recipe.getMaterials() != null) {
            for (ItemStack material : recipe.getMaterials()) {
                if (material != null && slot < 35) {
                    inv.setItem(slot, material.clone());
                    slot++;
                }
            }
        }

        ItemStack result = recipe.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Result");
            result.setItemMeta(meta);
        }
        inv.setItem(40, result);

        boolean canCraft = recipeManager.canCraft(player.getInventory().getContents(), recipe);

        ItemStack craftButton = new ItemStack(canCraft ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
        ItemMeta craftMeta = craftButton.getItemMeta();
        if (craftMeta != null) {
            craftMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, ACTION_CRAFT);
            craftMeta.getPersistentDataContainer().set(keyRecipeId, PersistentDataType.STRING, recipe.getId());

            List<String> lore = new ArrayList<>();

            if (!recipe.canCraftMore()) {
                craftMeta.setDisplayName(ChatColor.RED + "✗ Out of Crafts");
                lore.add(ChatColor.GRAY + "This recipe has reached its craft limit.");
            } else if (canCraft) {
                craftMeta.setDisplayName(ChatColor.GREEN + "✓ CRAFT");
                lore.add(ChatColor.GRAY + "Click to craft this item!");
            } else {
                craftMeta.setDisplayName(ChatColor.RED + "✗ Cannot Craft");
                lore.add(ChatColor.GRAY + "You don't have enough materials!");
            }

            lore.add("");

            if (recipe.isUnlimited()) {
                lore.add(ChatColor.AQUA + "Craft Limit: " + ChatColor.WHITE + "Unlimited");
            } else {
                lore.add(ChatColor.AQUA + "Crafts Left: " + ChatColor.WHITE + recipe.remainingCrafts());
            }

            craftMeta.setLore(lore);
            craftButton.setItemMeta(craftMeta);
        }
        inv.setItem(49, craftButton);

        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.YELLOW + "← Back");
            backMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, ACTION_BACK);
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(45, backButton);

        player.openInventory(inv);
    }

    private ItemStack createInfoItem(CustomRecipe recipe) {
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + recipe.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "REQUIREMENTS:");

            if (recipe.getMaterials() != null) {
                for (ItemStack material : recipe.getMaterials()) {
                    if (material != null) {
                        lore.add(ChatColor.RED + "  " + material.getAmount() + "x " +
                                formatMaterialName(material.getType()));
                    }
                }
            }

            lore.add("");
            if (recipe.isUnlimited()) {
                lore.add(ChatColor.AQUA + "Craft Limit: " + ChatColor.WHITE + "Unlimited");
            } else {
                lore.add(ChatColor.AQUA + "Crafts Left: " + ChatColor.WHITE + recipe.remainingCrafts());
            }

            meta.setLore(lore);
            info.setItemMeta(meta);
        }
        return info;
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    private String clampTitle(String title, int maxLen) {
        if (title == null) return "";
        return title.length() <= maxLen ? title : title.substring(0, maxLen);
    }
}