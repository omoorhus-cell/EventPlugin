package org.tekkabyte.eventPlugin.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.gui.AdminGUI;
import org.tekkabyte.eventPlugin.managers.RecipeManager;

public class CraftAdminCommand implements CommandExecutor {
    private final EventPlugin plugin;
    private final RecipeManager recipeManager;
    private final AdminGUI adminGUI;

    public CraftAdminCommand(EventPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.adminGUI = new AdminGUI(plugin, recipeManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("craftevents.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        adminGUI.openAdminMenu(player);
        return true;
    }

    public AdminGUI getAdminGUI() {
        return adminGUI;
    }
}