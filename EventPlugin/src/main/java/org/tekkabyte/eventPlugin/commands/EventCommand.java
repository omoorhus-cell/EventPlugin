package org.tekkabyte.eventPlugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.managers.EventManager;

public class EventCommand implements CommandExecutor {
    private final EventPlugin plugin;

    public EventCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        EventManager manager = plugin.getEventManager();

        switch (args[0].toLowerCase()) {
            case "create":
                if (!sender.hasPermission("eventplugin.start")) {
                    sender.sendMessage("§cYou don't have permission to create events!");
                    return true;
                }
                if (manager.isEventActive()) {
                    sender.sendMessage(plugin.getMessage("event-already-created"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /event create <uhc|tournament> [1v1|2v2]");
                    return true;
                }
                handleCreate(sender, args);
                break;

            case "start":
                if (!sender.hasPermission("eventplugin.start")) {
                    sender.sendMessage("§cYou don't have permission to start events!");
                    return true;
                }
                if (!manager.isEventActive()) {
                    sender.sendMessage(plugin.getMessage("event-not-created"));
                    return true;
                }
                manager.startMatch();
                break;

            case "join":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can join events!");
                    return true;
                }
                if (!manager.isEventActive()) {
                    p.sendMessage(plugin.getMessage("no-active-event"));
                    return true;
                }
                if (manager.isPlayerInEvent(p)) {
                    p.sendMessage(plugin.getMessage("already-in-event"));
                    return true;
                }
                manager.addPlayer(p);
                if (manager.isPlayerInEvent(p)) {
                    p.sendMessage(plugin.getMessage("joined-event"));
                }
                break;

            case "leave":
                if (!(sender instanceof Player p2)) {
                    sender.sendMessage("§cOnly players can leave events!");
                    return true;
                }
                if (!manager.isPlayerInEvent(p2)) {
                    p2.sendMessage(plugin.getMessage("not-in-event"));
                    return true;
                }
                manager.removePlayer(p2);
                p2.sendMessage(plugin.getMessage("left-event"));
                break;

            case "end":
                if (!sender.hasPermission("eventplugin.start")) {
                    sender.sendMessage("§cYou don't have permission to end events!");
                    return true;
                }
                if (!manager.isEventActive()) {
                    sender.sendMessage(plugin.getMessage("no-active-event"));
                    return true;
                }
                String type = manager.getActiveEventType();
                manager.endEvent();
                sender.sendMessage(plugin.getMessage("event-ended").replace("{type}", type));
                break;

            case "info":
                if (!manager.isEventActive()) {
                    sender.sendMessage("§e[Event] No active event");
                    return true;
                }
                sender.sendMessage("§e[Event] Active Event Info:");
                sender.sendMessage("§e  Type: " + manager.getActiveEventType());
                sender.sendMessage("§e  Players: " + manager.getPlayerCount());
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        EventManager manager = plugin.getEventManager();

        String type = args[1].toLowerCase();
        String mode = (args.length >= 3) ? args[2].toLowerCase() : "1v1";

        switch (type) {
            case "uhc":
                manager.createUHC();
                sender.sendMessage(plugin.getMessage("event-created").replace("{type}", "UHC"));
                break;

            case "tournament":
                if (!mode.equals("1v1") && !mode.equals("2v2")) mode = "1v1";
                manager.createTournament(mode);
                sender.sendMessage(plugin.getMessage("event-created").replace("{type}", "Tournament (" + mode + ")"));
                break;

            case "1v1":
            case "2v2":
                manager.createTournament(type);
                String eventBc = plugin.getMessage("event-created").replace("{type}", "Tournament (" + type + ")");
                Bukkit.broadcastMessage(eventBc);
                break;

            default:
                sender.sendMessage("§cInvalid event type! Use 'uhc' or 'tournament' (with 1v1/2v2).");
                sender.sendMessage("§cUsage: /event create tournament <1v1|2v2>");
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e=== Event Commands ===");
        sender.sendMessage("§e/event create uhc §7- Create a UHC event (prep world/lobby)");
        sender.sendMessage("§e/event create tournament <1v1|2v2> §7- Create a tournament");
        sender.sendMessage("§e/event start §7- Start the created event");
        sender.sendMessage("§e/event join §7- Join the created event");
        sender.sendMessage("§e/event leave §7- Leave the event");
        sender.sendMessage("§e/event end §7- End the event");
        sender.sendMessage("§e/event info §7- View event information");
    }
}
