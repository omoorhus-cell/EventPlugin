package org.tekkabyte.eventPlugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.gui.CraftingGUI;
import org.tekkabyte.eventPlugin.managers.RecipeManager;

public class CraftCommand implements CommandExecutor {

    private final EventPlugin plugin;
    private final CraftingGUI craftingGUI;

    public CraftCommand(EventPlugin plugin, RecipeManager recipeManager, CraftingGUI craftingGUI) {
        this.plugin = plugin;
        this.craftingGUI = craftingGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run from the console.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /ecraft <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> craftingGUI.openCraftingMenu(target));

        sender.sendMessage(ChatColor.GREEN + "Opened crafting menu for " + target.getName());
        return true;
    }

    public CraftingGUI getCraftingGUI() {
        return craftingGUI;
    }
}