package org.tekkabyte.eventPlugin.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.events.BaseEvent;
import org.tekkabyte.eventPlugin.events.UHCEvent;

public class UHCWaitingBoxProtectListener implements Listener {
    private final EventPlugin plugin;

    public UHCWaitingBoxProtectListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BaseEvent ev = plugin.getEventManager().getActiveEvent();
        if (!(ev instanceof UHCEvent uhc)) return;
        if (!uhc.isWaitingPhase()) return;

        Location loc = event.getBlock().getLocation();
        if (!uhc.isInWaitingBox(loc)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage("§c[UHC] You can't break blocks in the waiting box.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        BaseEvent ev = plugin.getEventManager().getActiveEvent();
        if (!(ev instanceof UHCEvent uhc)) return;
        if (!uhc.isWaitingPhase()) return;

        Location loc = event.getBlock().getLocation();
        if (!uhc.isInWaitingBox(loc)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage("§c[UHC] You can't place blocks in the waiting box.");
    }
}
