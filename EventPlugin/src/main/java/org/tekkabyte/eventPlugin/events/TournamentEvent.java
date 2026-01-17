package org.tekkabyte.eventPlugin.events;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.utils.TeleportUtil;

import java.util.*;
import java.util.stream.Collectors;

public class TournamentEvent extends BaseEvent {

    public enum Mode {
        ONE_V_ONE(2),
        TWO_V_TWO(4);
        public final int playersPerMatch;
        Mode(int n) { this.playersPerMatch = n; }
    }

    private enum Phase { CREATED, RUNNING }
    private Phase phase = Phase.CREATED;

    private Mode mode = Mode.ONE_V_ONE;

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> currentMatch = new HashSet<>();
    private final Set<UUID> inMatchAlive = new HashSet<>();
    private final Map<UUID, Integer> teamMap = new HashMap<>();
    private final Deque<UUID> queue = new ArrayDeque<>();

    private final Map<UUID, Location> returnLocations = new HashMap<>();

    private boolean usingConfiguredWorld = false;

    public TournamentEvent(EventPlugin plugin) {
        super(plugin);
    }

    public void setMode(Mode mode) {
        this.mode = (mode == null) ? Mode.ONE_V_ONE : mode;
    }

    @Override
    public void create() {
        usingConfiguredWorld = false;

        String configuredWorldName = plugin.getConfig().getString("tournament.world", "");
        if (configuredWorldName != null) configuredWorldName = configuredWorldName.trim();

        if (configuredWorldName != null && !configuredWorldName.isEmpty()) {
            World w = Bukkit.getWorld(configuredWorldName);

            if (w == null) {
                boolean createIfMissing = plugin.getConfig().getBoolean("tournament.create-world-if-missing", true);
                if (!createIfMissing) {
                    plugin.getLogger().severe("Tournament world '" + configuredWorldName + "' is not loaded, and create-world-if-missing=false.");
                    return;
                }
                w = Bukkit.createWorld(new WorldCreator(configuredWorldName));
            }

            if (w == null) {
                plugin.getLogger().severe("Failed to load/create configured Tournament world: " + configuredWorldName);
                return;
            }

            eventWorld = w;
            usingConfiguredWorld = true;
        } else {
            String worldName = generateWorldName(plugin.getConfig().getString("tournament.world-name-prefix", "tournament_"));

            eventWorld = plugin.getWorldManager().createTournamentWorld(worldName);
            if (eventWorld == null) {
                plugin.getLogger().severe("Failed to create Tournament world!");
                return;
            }
        }

        int arenaSize = plugin.getConfig().getInt("tournament.arena-size", 100);
        plugin.getWorldManager().setupWorldBorder(eventWorld, arenaSize, 0, 0);

        eventWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        eventWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        eventWorld.setGameRule(GameRule.KEEP_INVENTORY, false);
        eventWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        eventWorld.setWeatherDuration(0);

        eventWorld.setPVP(false);

        active = true;
        phase = Phase.CREATED;

        plugin.getLogger().info("Tournament created: world=" + eventWorld.getName() + " (configured=" + usingConfiguredWorld + ") mode=" + mode);
    }

    @Override
    public void startMatch() {
        if (!active || eventWorld == null) return;
        if (phase == Phase.RUNNING) return;

        eventWorld.setPVP(true);

        participants.clear();
        participants.addAll(plugin.getEventManager().getEventPlayerUUIDs());

        if (participants.size() < mode.playersPerMatch) {
            Bukkit.broadcastMessage("§c[Tournament] Not enough players to start (" +
                    participants.size() + "/" + mode.playersPerMatch + ").");
            return;
        }

        queue.clear();
        List<UUID> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        queue.addAll(shuffled);

        phase = Phase.RUNNING;

        Bukkit.broadcastMessage(plugin.getMessage("event-started").replace("{type}",
                mode == Mode.ONE_V_ONE ? "Tournament (1v1)" : "Tournament (2v2)"));

        teleportAllToStartLocation();

        setAllWaitingSpectator();
        startNextMatch();
    }

    @Override
    public void end() {
        if (eventWorld == null) return;

        Set<UUID> toTry = new HashSet<>();
        toTry.addAll(plugin.getEventManager().getEventPlayerUUIDs());
        toTry.addAll(returnLocations.keySet());

        for (UUID uuid : toTry) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            if (p.getWorld() != eventWorld && !returnLocations.containsKey(uuid)) continue;

            if (!restoreReturnLocationIfPresent(p)) {
                World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (fallback != null) TeleportUtil.teleportRetainingRideStack(plugin, p, fallback.getSpawnLocation());
            }
        }

        String worldName = eventWorld.getName();

        participants.clear();
        queue.clear();
        currentMatch.clear();
        inMatchAlive.clear();
        teamMap.clear();
        returnLocations.clear();

        boolean deleteWorld = plugin.getConfig().getBoolean("tournament.delete-world-on-end", true);
        if (!usingConfiguredWorld && deleteWorld) {
            plugin.getWorldManager().deleteWorld(worldName);
        }

        active = false;
        eventWorld = null;
        plugin.getLogger().info("Tournament ended");
    }

    @Override
    public void onPlayerJoin(Player player) {
        if (eventWorld == null) return;

        if (phase == Phase.RUNNING) {
            player.sendMessage(plugin.getMessage("event-already-started"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            saveReturnLocation(player);

            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();

            player.setGameMode(GameMode.SPECTATOR);
            TeleportUtil.teleportRetainingRideStack(plugin, player, getLobbyLocation());

            player.sendMessage(plugin.getMessage("tournament-joined-lobby"));
        });
    }

    @Override
    public void onPlayerLeave(Player player) {
        UUID uuid = player.getUniqueId();

        participants.remove(uuid);
        queue.remove(uuid);

        Bukkit.getScheduler().runTask(plugin, () -> restoreReturnLocationIfPresent(player));

        if (currentMatch.contains(uuid)) {
            inMatchAlive.remove(uuid);
            currentMatch.remove(uuid);
            teamMap.remove(uuid);
            Bukkit.getScheduler().runTaskLater(plugin, this::resolveMatchIfComplete, 1L);
        }
    }

    @Override
    public boolean canJoin(Player player) {
        return phase != Phase.RUNNING;
    }

    @Override
    public String getJoinDenyMessageKey() {
        return "event-already-started";
    }

    @Override
    public boolean onPlayerDeath(Player player) {
        if (!active || eventWorld == null) return false;
        if (player.getWorld() != eventWorld) return false;

        UUID uuid = player.getUniqueId();
        if (!currentMatch.contains(uuid)) return false;

        inMatchAlive.remove(uuid);
        participants.remove(uuid);
        queue.remove(uuid);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            player.getInventory().clear();
            player.setHealth(20.0);
            player.setFoodLevel(20);

            player.setGameMode(GameMode.SPECTATOR);
            TeleportUtil.teleportRetainingRideStack(plugin, player, getLobbyLocation());
            player.sendMessage(plugin.getMessage("tournament-eliminated"));
        }, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, this::resolveMatchIfComplete, 2L);
        return true;
    }

    @Override
    public String getEventType() {
        return "Tournament";
    }

    private void saveReturnLocation(Player player) {
        UUID uuid = player.getUniqueId();
        returnLocations.putIfAbsent(uuid, player.getLocation().clone());
    }

    private boolean restoreReturnLocationIfPresent(Player player) {
        UUID uuid = player.getUniqueId();
        Location back = returnLocations.remove(uuid);
        if (back == null) return false;

        World w = back.getWorld();
        if (w == null) {
            World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (fallback == null) return false;
            back = fallback.getSpawnLocation();
        }

        TeleportUtil.teleportRetainingRideStack(plugin, player, back);
        return true;
    }


    private void teleportAllToStartLocation() {
        Location start = getStartLocation();
        for (UUID uuid : plugin.getEventManager().getEventPlayerUUIDs()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            saveReturnLocation(p);

            p.setGameMode(GameMode.SPECTATOR);
            TeleportUtil.teleportRetainingRideStack(plugin, p, start);
        }
    }

    private Location getLobbyLocation() {
        boolean lobbySet = plugin.getConfig().getBoolean("tournament.lobby.set", false);
        boolean legacySet = plugin.getConfig().getBoolean("general.spawn-location-set", false);

        if (lobbySet) {
            double x = plugin.getConfig().getDouble("tournament.lobby.x");
            double y = plugin.getConfig().getDouble("tournament.lobby.y");
            double z = plugin.getConfig().getDouble("tournament.lobby.z");
            float yaw = (float) plugin.getConfig().getDouble("tournament.lobby.yaw", 0.0);
            float pitch = (float) plugin.getConfig().getDouble("tournament.lobby.pitch", 0.0);
            return new Location(eventWorld, x + 0.5, y, z + 0.5, yaw, pitch);
        }

        if (legacySet) {
            double x = plugin.getConfig().getDouble("general.tournament-spawn-x");
            double y = plugin.getConfig().getDouble("general.tournament-spawn-y");
            double z = plugin.getConfig().getDouble("general.tournament-spawn-z");
            return new Location(eventWorld, x + 0.5, y, z + 0.5);
        }

        return new Location(eventWorld, 0.5, 66, 0.5);
    }

    private Location getStartLocation() {
        if (plugin.getConfig().getBoolean("tournament.start.set", false)) {
            double x = plugin.getConfig().getDouble("tournament.start.x");
            double y = plugin.getConfig().getDouble("tournament.start.y");
            double z = plugin.getConfig().getDouble("tournament.start.z");
            float yaw = (float) plugin.getConfig().getDouble("tournament.start.yaw", 0.0);
            float pitch = (float) plugin.getConfig().getDouble("tournament.start.pitch", 0.0);
            return new Location(eventWorld, x + 0.5, y, z + 0.5, yaw, pitch);
        }

        return getLobbyLocation();
    }

    private Location getTeamSpawnFromConfig(int team) {
        String base = team == 0 ? "tournament.match-spawns.team0" : "tournament.match-spawns.team1";
        if (!plugin.getConfig().getBoolean(base + ".set", false)) return null;

        double x = plugin.getConfig().getDouble(base + ".x");
        double y = plugin.getConfig().getDouble(base + ".y");
        double z = plugin.getConfig().getDouble(base + ".z");
        float yaw = (float) plugin.getConfig().getDouble(base + ".yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble(base + ".pitch", 0.0);

        return new Location(eventWorld, x + 0.5, y, z + 0.5, yaw, pitch);
    }

    private Location getArenaSpawn(int team) {
        Location configured = getTeamSpawnFromConfig(team);
        if (configured != null) return configured;

        int arenaSize = plugin.getConfig().getInt("tournament.arena-size", 100);
        int half = Math.max(10, arenaSize / 2 - 10);

        double x = (team == 0) ? -half : half;
        double z = 0;
        int y = eventWorld.getHighestBlockYAt((int) x, (int) z) + 1;
        return new Location(eventWorld, x + 0.5, y, z + 0.5);
    }


    private void setAllWaitingSpectator() {
        for (UUID uuid : plugin.getEventManager().getEventPlayerUUIDs()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getWorld() != eventWorld) continue;
            if (currentMatch.contains(uuid)) continue;

            p.setGameMode(GameMode.SPECTATOR);
            TeleportUtil.teleportRetainingRideStack(plugin, p, getLobbyLocation());
        }
    }

    private void startNextMatch() {
        currentMatch.clear();
        inMatchAlive.clear();
        teamMap.clear();

        queue.removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            return p == null || !p.isOnline() || !participants.contains(uuid);
        });

        if (mode == Mode.ONE_V_ONE) {
            if (participants.size() <= 1) { announceWinner(); return; }
        } else {
            if (participants.size() <= 2) { announceWinner(); return; }
        }

        if (queue.size() < mode.playersPerMatch) {
            List<UUID> left = new ArrayList<>(participants);
            Collections.shuffle(left);
            queue.clear();
            queue.addAll(left);
            if (queue.size() < mode.playersPerMatch) { announceWinner(); return; }
        }

        List<UUID> picked = new ArrayList<>(mode.playersPerMatch);
        while (picked.size() < mode.playersPerMatch && !queue.isEmpty()) {
            UUID u = queue.pollFirst();
            if (participants.contains(u)) picked.add(u);
        }
        if (picked.size() < mode.playersPerMatch) { announceWinner(); return; }

        if (mode == Mode.ONE_V_ONE) {
            teamMap.put(picked.get(0), 0);
            teamMap.put(picked.get(1), 1);
        } else {
            teamMap.put(picked.get(0), 0);
            teamMap.put(picked.get(1), 0);
            teamMap.put(picked.get(2), 1);
            teamMap.put(picked.get(3), 1);
        }

        currentMatch.addAll(picked);
        inMatchAlive.addAll(picked);

        setAllWaitingSpectator();

        Location aSpawn = getArenaSpawn(0);
        Location bSpawn = getArenaSpawn(1);

        for (UUID uuid : picked) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            p.setGameMode(GameMode.SURVIVAL);
            TeleportUtil.teleportRetainingRideStack(plugin, p, teamMap.get(uuid) == 0 ? aSpawn : bSpawn);
            prepFighter(p);
        }

        Bukkit.broadcastMessage("§e[Tournament] Match starting: " +
                formatTeamList(0) + " §7vs§r " + formatTeamList(1));
    }

    private void resolveMatchIfComplete() {
        if (currentMatch.isEmpty()) return;

        int aliveTeam0 = 0, aliveTeam1 = 0;
        for (UUID uuid : currentMatch) {
            if (!inMatchAlive.contains(uuid)) continue;
            Integer team = teamMap.get(uuid);
            if (team == null) continue;
            if (team == 0) aliveTeam0++;
            else aliveTeam1++;
        }

        if (aliveTeam0 > 0 && aliveTeam1 > 0) return;

        int winnerTeam = (aliveTeam0 > 0) ? 0 : 1;

        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();

        for (UUID u : currentMatch) {
            Integer team = teamMap.get(u);
            if (team == null) continue;

            if (team == winnerTeam && inMatchAlive.contains(u)) winners.add(u);
            if (team == (1 - winnerTeam)) losers.add(u);
        }

        for (UUID loser : losers) {
            participants.remove(loser);
            Player lp = Bukkit.getPlayer(loser);
            if (lp != null && lp.isOnline() && lp.getWorld() == eventWorld) {
                lp.setGameMode(GameMode.SPECTATOR);
                TeleportUtil.teleportRetainingRideStack(plugin, lp, getLobbyLocation());
                lp.sendMessage(plugin.getMessage("tournament-eliminated"));
            }
        }

        for (UUID win : winners) {
            Player p = Bukkit.getPlayer(win);
            if (p != null && p.isOnline()) {
                TeleportUtil.teleportRetainingRideStack(plugin, p, getLobbyLocation());
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 2));
                p.sendMessage(plugin.getMessage("tournament-match-win"));
            }
            queue.addLast(win);
        }

        Bukkit.broadcastMessage("§a[Tournament] Match ended. Winner: " +
                (winnerTeam == 0 ? formatTeamList(0) : formatTeamList(1)));

        currentMatch.clear();
        inMatchAlive.clear();
        teamMap.clear();

        Bukkit.getScheduler().runTaskLater(plugin, this::startNextMatch, 60L);
    }

    private void announceWinner() {
        if (participants.isEmpty()) {
            Bukkit.broadcastMessage("§c[Tournament] Tournament ended with no winner.");
            plugin.getEventManager().endEvent();
            return;
        }

        if (mode == Mode.ONE_V_ONE) {
            UUID w = participants.iterator().next();
            Player p = Bukkit.getPlayer(w);
            String name = (p != null) ? p.getName() : "Unknown";
            Bukkit.broadcastMessage("§6[Tournament] §eWinner: §6" + name);
            plugin.getEventManager().endEvent();
            return;
        }

        if (participants.size() <= 2) {
            String names = participants.stream()
                    .map(u -> {
                        Player p = Bukkit.getPlayer(u);
                        return p != null ? p.getName() : "Unknown";
                    })
                    .collect(Collectors.joining(" & "));
            Bukkit.broadcastMessage(plugin.getMessage("tournament-winning-team").replace("{players}", names));
            plugin.getEventManager().endEvent();
            return;
        }

        Bukkit.broadcastMessage("§6[Tournament] Tournament finished.");
        plugin.getEventManager().endEvent();
    }

    private void prepFighter(Player player) {
        player.setHealth(20.0);
        player.setFoodLevel(20);

        player.getInventory().clear();
        for (PotionEffect e : player.getActivePotionEffects()) {
            player.removePotionEffect(e.getType());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4));
        giveStarterKit(player);
    }

    private void giveStarterKit(Player player) {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
        player.getInventory().addItem(new ItemStack(Material.BOW));
        player.getInventory().addItem(new ItemStack(Material.ARROW, 64));

        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));

        player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 5));
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
    }

    private String formatTeamList(int team) {
        List<String> names = currentMatch.stream()
                .filter(u -> Objects.equals(teamMap.get(u), team))
                .map(u -> {
                    Player p = Bukkit.getPlayer(u);
                    return p != null ? p.getName() : "Unknown";
                })
                .collect(Collectors.toList());

        if (names.isEmpty()) return "§7(unknown)";
        if (names.size() == 1) return "§b" + names.get(0);
        return "§b" + String.join(" §7& §b", names);
    }
}