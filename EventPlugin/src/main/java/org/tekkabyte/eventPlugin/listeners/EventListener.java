package org.tekkabyte.eventPlugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.events.BaseEvent;

public class EventListener implements Listener {
    private final EventPlugin plugin;

    public EventListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getEventManager().restoreIfStale(event.getPlayer());

        if (!plugin.getEventManager().isEventActive()) {
            World main = Bukkit.getWorlds().get(0);
            if (event.getPlayer().getWorld() != main) {
                event.getPlayer().teleport(main.getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (plugin.getEventManager().isPlayerInEvent(player)) {
            plugin.getEventManager().removePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        var player = event.getEntity();

        if (!plugin.getEventManager().isPlayerInEvent(player)) return;

        BaseEvent active = plugin.getEventManager().getActiveEvent();
        if (active == null || active.getEventWorld() == null) return;
        if (player.getWorld() != active.getEventWorld()) return;

        active.onPlayerDeath(player);
    }
}
