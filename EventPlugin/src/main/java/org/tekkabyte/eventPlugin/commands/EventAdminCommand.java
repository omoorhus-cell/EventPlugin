package org.tekkabyte.eventPlugin.commands;

import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tekkabyte.eventPlugin.EventPlugin;

public class EventAdminCommand implements CommandExecutor {
    private final EventPlugin plugin;

    public EventAdminCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eventplugin.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setborder":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can set borders!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /eventadmin setborder <size>");
                    return true;
                }
                handleSetBorder((Player) sender, args[1]);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleSetBorder(Player player, String sizeStr) {
        try {
            double size = Double.parseDouble(sizeStr);
            World world = player.getWorld();
            WorldBorder border = world.getWorldBorder();
            border.setCenter(player.getLocation());
            border.setSize(size);
            player.sendMessage("§a[Event] World border set to " + size + " blocks!");
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid size! Please enter a number.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e=== Event Admin Commands ===");
        sender.sendMessage("§e/eventadmin setborder <size> §7- Set world border");
    }
}