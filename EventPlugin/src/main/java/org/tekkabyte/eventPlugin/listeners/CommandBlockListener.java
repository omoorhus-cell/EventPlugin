package org.tekkabyte.eventPlugin.listeners;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.events.BaseEvent;

import java.util.List;

public class CommandBlockListener implements Listener {
    private final EventPlugin plugin;

    public CommandBlockListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getEventManager().isPlayerInEvent(player)) {
            return;
        }

        BaseEvent activeEvent = plugin.getEventManager().getActiveEvent();
        if (activeEvent == null) {
            return;
        }

        World eventWorld = activeEvent.getEventWorld();
        if (eventWorld == null || !player.getWorld().equals(eventWorld)) {
            return;
        }

        String command = event.getMessage().toLowerCase().substring(1);
        String[] parts = command.split(" ");
        String baseCommand = parts[0];

        List<String> blockedCommands = plugin.getConfig().getStringList("general.blocked-commands");

        for (String blocked : blockedCommands) {
            if (baseCommand.equals(blocked.toLowerCase()) ||
                    baseCommand.startsWith(blocked.toLowerCase() + ":")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage("command-blocked"));
                return;
            }
        }
    }
}
