package org.tekkabyte.eventPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.events.BaseEvent;
import org.tekkabyte.eventPlugin.events.TournamentEvent;
import org.tekkabyte.eventPlugin.events.UHCEvent;

import java.util.*;

public class EventManager {
    private final EventPlugin plugin;
    private BaseEvent activeEvent;

    private final Set<UUID> eventPlayers = new HashSet<>();
    private final Map<UUID, PlayerState> savedStates = new HashMap<>();

    public EventManager(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public void createUHC() {
        if (activeEvent != null) return;
        activeEvent = new UHCEvent(plugin);
        activeEvent.create();
    }

    public void createTournament(String modeStr) {
        if (activeEvent != null) return;

        TournamentEvent ev = new TournamentEvent(plugin);
        TournamentEvent.Mode mode = TournamentEvent.Mode.ONE_V_ONE;
        if (modeStr != null && modeStr.equalsIgnoreCase("2v2")) mode = TournamentEvent.Mode.TWO_V_TWO;
        ev.setMode(mode);

        activeEvent = ev;
        activeEvent.create();
    }

    public void startMatch() {
        if (activeEvent == null) return;
        activeEvent.startMatch();
    }

    public void endEvent() {
        if (activeEvent == null) return;

        for (UUID uuid : new HashSet<>(eventPlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) removePlayer(p);
        }

        activeEvent.end();

        eventPlayers.clear();
        savedStates.clear();
        activeEvent = null;
    }

    public void addPlayer(Player player) {
        plugin.getLogger().info("ADD DEBUG: player=" + player.getName()
                + " activeEvent=" + (activeEvent == null ? "null" : activeEvent.getEventType())
                + " active=" + isEventActive());

        if (activeEvent == null || !activeEvent.isActive()) return;

        if (!activeEvent.canJoin(player)) {
            player.sendMessage(plugin.getMessage(activeEvent.getJoinDenyMessageKey()));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (eventPlayers.contains(uuid)) return;

        savedStates.put(uuid, new PlayerState(player));
        eventPlayers.add(uuid);

        activeEvent.onPlayerJoin(player);
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!eventPlayers.remove(uuid)) return;

        if (activeEvent != null) activeEvent.onPlayerLeave(player);

        PlayerState st = savedStates.remove(uuid);
        if (st != null) st.restore(player);
    }

    public void restoreIfStale(Player player) {
        if (isEventActive()) return;

        UUID uuid = player.getUniqueId();
        PlayerState st = savedStates.remove(uuid);
        if (st != null) st.restore(player);
        eventPlayers.remove(uuid);
    }

    public boolean isEventActive() {
        return activeEvent != null && activeEvent.isActive();
    }

    public boolean isPlayerInEvent(Player player) {
        return eventPlayers.contains(player.getUniqueId());
    }

    public String getActiveEventType() {
        return activeEvent != null ? activeEvent.getEventType() : "None";
    }

    public int getPlayerCount() {
        return eventPlayers.size();
    }

    public BaseEvent getActiveEvent() {
        return activeEvent;
    }

    public Set<UUID> getEventPlayerUUIDs() {
        return Collections.unmodifiableSet(eventPlayers);
    }

    public void shutdown() {
        if (activeEvent != null) endEvent();
    }
}