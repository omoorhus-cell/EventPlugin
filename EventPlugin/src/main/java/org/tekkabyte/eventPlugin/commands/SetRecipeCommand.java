package org.tekkabyte.eventPlugin.commands;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.managers.RecipeManager;
import org.tekkabyte.eventPlugin.models.CustomRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SetRecipeCommand implements CommandExecutor {

    private final EventPlugin plugin;
    private final RecipeManager recipeManager;

    public SetRecipeCommand(EventPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("craftevents.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /setrecipe [maxCrafts|unlimited] <name>");
            player.sendMessage(ChatColor.YELLOW + "Hold the RESULT item. Put ingredients in your inventory.");
            return true;
        }

        int maxCrafts = -1;
        int nameStart = 0;

        String first = args[0];
        if (first.equalsIgnoreCase("unlimited")) {
            maxCrafts = -1;
            nameStart = 1;
        } else {
            try {
                maxCrafts = Integer.parseInt(first);
                nameStart = 1;
                if (maxCrafts < 0) maxCrafts = -1;
            } catch (NumberFormatException ignored) {
                maxCrafts = -1;
                nameStart = 0;
            }
        }

        if (args.length - nameStart < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /setrecipe [maxCrafts|unlimited] <name>");
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = nameStart; i < args.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        String name = sb.toString();

        PlayerInventory inv = player.getInventory();
        ItemStack result = inv.getItemInMainHand();

        if (result == null || result.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must hold the RESULT item in your main hand.");
            return true;
        }

        int heldSlot = inv.getHeldItemSlot();
        ItemStack[] contents = inv.getContents();

        List<ItemStack> materials = new ArrayList<>();

        for (int i = 0; i < 36; i++) {
            if (i == heldSlot) continue;

            ItemStack it = contents[i];
            if (it == null || it.getType() == Material.AIR) continue;

            materials.add(it.clone());
        }

        if (materials.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You must have at least 1 ingredient in your inventory (not counting the held item).");
            return true;
        }

        String id = name.toLowerCase().replace(" ", "_") + "_" + UUID.randomUUID().toString().substring(0, 8);

        CustomRecipe recipe = new CustomRecipe(id, name, result.clone(), materials, maxCrafts, 0);
        recipeManager.saveRecipe(recipe);

        player.sendMessage(ChatColor.GREEN + "Created recipe: " + name);
        player.sendMessage(ChatColor.YELLOW + "ID: " + id);
        if (maxCrafts < 0) {
            player.sendMessage(ChatColor.AQUA + "Craft Limit: " + ChatColor.WHITE + "Unlimited");
        } else {
            player.sendMessage(ChatColor.AQUA + "Craft Limit: " + ChatColor.WHITE + maxCrafts);
        }
        return true;
    }
}