package org.tekkabyte.eventPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.database.DatabaseManager;
import org.tekkabyte.eventPlugin.models.CustomRecipe;

import java.util.*;

public class RecipeManager {
    private final EventPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, CustomRecipe> recipes;

    public RecipeManager(EventPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.recipes = new HashMap<>();
    }

    public void loadRecipes() {
        recipes.clear();
        List<CustomRecipe> loadedRecipes = databaseManager.loadAllRecipes();
        for (CustomRecipe recipe : loadedRecipes) {
            recipes.put(recipe.getId(), recipe);
        }
        plugin.getLogger().info("Loaded " + recipes.size() + " custom recipes from database.");
    }

    public void saveRecipe(CustomRecipe recipe) {
        recipes.put(recipe.getId(), recipe);
        databaseManager.saveRecipe(recipe);
    }

    public void deleteRecipe(String id) {
        recipes.remove(id);
        databaseManager.deleteRecipe(id);
    }

    public CustomRecipe getRecipe(String id) {
        return recipes.get(id);
    }

    public List<CustomRecipe> getAllRecipes() {
        return new ArrayList<>(recipes.values());
    }

    public boolean canCraft(ItemStack[] inventory, CustomRecipe recipe) {
        if (recipe == null) return false;
        if (!recipe.canCraftMore()) return false;

        Map<ItemStack, Integer> required = new HashMap<>();

        for (ItemStack material : recipe.getMaterials()) {
            if (material == null) continue;

            boolean found = false;
            for (ItemStack key : required.keySet()) {
                if (key.isSimilar(material)) {
                    required.put(key, required.get(key) + material.getAmount());
                    found = true;
                    break;
                }
            }
            if (!found) required.put(material.clone(), material.getAmount());
        }

        Map<ItemStack, Integer> available = new HashMap<>();
        for (ItemStack item : inventory) {
            if (item == null) continue;

            boolean found = false;
            for (ItemStack key : available.keySet()) {
                if (key.isSimilar(item)) {
                    available.put(key, available.get(key) + item.getAmount());
                    found = true;
                    break;
                }
            }
            if (!found) available.put(item.clone(), item.getAmount());
        }

        for (Map.Entry<ItemStack, Integer> entry : required.entrySet()) {
            ItemStack requiredItem = entry.getKey();
            int requiredAmount = entry.getValue();

            int availableAmount = 0;
            for (Map.Entry<ItemStack, Integer> availEntry : available.entrySet()) {
                if (availEntry.getKey().isSimilar(requiredItem)) {
                    availableAmount = availEntry.getValue();
                    break;
                }
            }

            if (availableAmount < requiredAmount) return false;
        }

        return true;
    }

    public void removeMaterials(ItemStack[] inventory, CustomRecipe recipe) {
        Map<ItemStack, Integer> toRemove = new HashMap<>();

        for (ItemStack material : recipe.getMaterials()) {
            if (material == null) continue;

            boolean found = false;
            for (ItemStack key : toRemove.keySet()) {
                if (key.isSimilar(material)) {
                    toRemove.put(key, toRemove.get(key) + material.getAmount());
                    found = true;
                    break;
                }
            }
            if (!found) toRemove.put(material.clone(), material.getAmount());
        }

        for (Map.Entry<ItemStack, Integer> entry : toRemove.entrySet()) {
            ItemStack itemToRemove = entry.getKey();
            int amountToRemove = entry.getValue();

            for (ItemStack item : inventory) {
                if (item == null) continue;
                if (!item.isSimilar(itemToRemove)) continue;

                if (item.getAmount() > amountToRemove) {
                    item.setAmount(item.getAmount() - amountToRemove);
                    break;
                } else {
                    amountToRemove -= item.getAmount();
                    item.setAmount(0);
                    item.setType(org.bukkit.Material.AIR);
                    if (amountToRemove <= 0) break;
                }
            }
        }
    }

    public boolean tryCraftAndConsume(ItemStack[] inventory, CustomRecipe recipe) {
        return tryCraftAndConsume(null, inventory, recipe);
    }

    public boolean tryCraftAndConsume(Player crafter, ItemStack[] inventory, CustomRecipe recipe) {
        if (!canCraft(inventory, recipe)) return false;

        removeMaterials(inventory, recipe);

        recipe.incrementCraftsUsed(1);
        saveRecipe(recipe);

        broadcastCraft(crafter, recipe.getResult());
        playWitherSpawnToAll();

        return true;
    }

    private void broadcastCraft(Player crafter, ItemStack result) {
        String itemName = prettyItemName(result);

        String msg;
        if (crafter != null) {
            msg = "§x§F§F§C§B§4§0✦ EVENT CRAFT ✦ " + ChatColor.WHITE + crafter.getName() + " crafted the " + itemName + "!";
        } else {
            msg = "§x§F§F§C§B§4§0✦ EVENT CRAFT ✦ " + ChatColor.WHITE + "An item was crafted: " + itemName + "!";
        }

        Bukkit.broadcastMessage(msg);
    }

    private void playWitherSpawnToAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }
    }

    private String prettyItemName(ItemStack item) {
        if (item == null) return "Unknown Item";

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        String type = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = type.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}