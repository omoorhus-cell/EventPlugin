package org.tekkabyte.eventPlugin.events;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.tekkabyte.eventPlugin.EventPlugin;

public abstract class BaseEvent {
    protected final EventPlugin plugin;
    protected World eventWorld;
    protected boolean active;

    protected BaseEvent(EventPlugin plugin) {
        this.plugin = plugin;
        this.active = false;
    }

    public abstract void create();

    public abstract void startMatch();

    public abstract void end();

    public abstract void onPlayerJoin(Player player);

    public abstract void onPlayerLeave(Player player);

    public abstract boolean onPlayerDeath(Player player);

    public abstract boolean canJoin(Player player);

    public abstract String getJoinDenyMessageKey();

    public abstract String getEventType();

    public World getEventWorld() {
        return eventWorld;
    }

    public boolean isActive() {
        return active;
    }

    protected String generateWorldName(String prefix) {
        return prefix + System.currentTimeMillis();
    }
}
