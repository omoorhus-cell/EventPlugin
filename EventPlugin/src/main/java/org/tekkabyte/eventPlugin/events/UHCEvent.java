package org.tekkabyte.eventPlugin.events;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.utils.TeleportUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class UHCEvent extends BaseEvent {

    private enum Phase { CREATED, RUNNING }
    private Phase phase = Phase.CREATED;

    private final Set<UUID> eliminated = new HashSet<>();

    private int boxMinX, boxMaxX, boxMinY, boxMaxY, boxMinZ, boxMaxZ;

    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public UHCEvent(EventPlugin plugin) {
        super(plugin);
    }

    @Override
    public void create() {
        String worldName = generateWorldName(plugin.getConfig().getString("uhc.world-name-prefix", "uhc_"));

        eventWorld = plugin.getWorldManager().createEventWorld(worldName, World.Environment.NORMAL);

        plugin.getLogger().info("UHC DEBUG: create() worldName=" + worldName
                + " loaded=" + (Bukkit.getWorld(worldName) != null)
                + " eventWorld=" + (eventWorld == null ? "null" : eventWorld.getName()));

        if (eventWorld == null) {
            plugin.getLogger().severe("Failed to create UHC world!");
            return;
        }

        int borderSize = plugin.getConfig().getInt("uhc.world-border-size", 1000);
        plugin.getWorldManager().setupWorldBorder(eventWorld, borderSize, 0, 0);

        boolean naturalRegen = plugin.getConfig().getBoolean("uhc.natural-regen", false);
        eventWorld.setGameRule(GameRule.NATURAL_REGENERATION, naturalRegen);

        eventWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        eventWorld.setDifficulty(Difficulty.HARD);

        // waiting phase protections
        eventWorld.setPVP(false);

        phase = Phase.CREATED;
        active = true;

        if (plugin.getConfig().getBoolean("uhc.waiting-box.enabled", true)) {
            buildWaitingBox();
        }

        plugin.getLogger().info("UHC Event created in world: " + worldName);
    }

    @Override
    public void startMatch() {
        if (eventWorld == null || !active) return;
        if (phase == Phase.RUNNING) return;

        eventWorld.setPVP(true);

        for (UUID uuid : plugin.getEventManager().getEventPlayerUUIDs()) {
            if (eliminated.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            prepMatchPlayer(p);
            scatterPlayer(p);
        }

        int shrinkTime = plugin.getConfig().getInt("uhc.world-border-shrink-time", 3600);
        int finalSize = plugin.getConfig().getInt("uhc.world-border-final-size", 100);
        plugin.getWorldManager().shrinkWorldBorder(eventWorld, finalSize, shrinkTime);

        phase = Phase.RUNNING;

        Bukkit.broadcastMessage(plugin.getMessage("event-started").replace("{type}", "UHC"));
        plugin.getLogger().info("UHC match started.");
    }

    @Override
    public void end() {
        if (eventWorld == null) return;

        for (UUID uuid : plugin.getEventManager().getEventPlayerUUIDs()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            restoreReturnLocation(p);
        }

        String worldName = eventWorld.getName();
        boolean deleteWorld = plugin.getConfig().getBoolean("uhc.delete-world-on-end", true);

        eliminated.clear();
        returnLocations.clear();

        if (deleteWorld) plugin.getWorldManager().deleteWorld(worldName);

        active = false;
        eventWorld = null;

        plugin.getLogger().info("UHC Event ended");
    }

    @Override
    public void onPlayerJoin(Player player) {
        plugin.getLogger().info("UHC DEBUG: onPlayerJoin " + player.getName()
                + " eventWorld=" + (eventWorld == null ? "null" : eventWorld.getName())
                + " phase=" + phase);

        if (eventWorld == null) {
            player.sendMessage("§c[UHC] World isn't ready. Try again.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            saveReturnLocation(player);

            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();

            player.setGameMode(GameMode.ADVENTURE);

            Location lobby = plugin.getConfig().getBoolean("uhc.waiting-box.enabled", true)
                    ? getWaitingBoxSpawn()
                    : new Location(eventWorld, 0.5, 66, 0.5);

            plugin.getLogger().info("UHC DEBUG: teleporting " + player.getName()
                    + " to lobby=" + fmt(lobby));

            TeleportUtil.teleportRetainingRideStack(plugin, player, lobby);
            player.sendMessage(plugin.getMessage("uhc-join-box"));
        });
    }

    @Override
    public void onPlayerLeave(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> restoreReturnLocation(player));
    }

    @Override
    public boolean canJoin(Player player) {
        if (plugin.getConfig().getBoolean("uhc.deny-rejoin-after-death", true)
                && eliminated.contains(player.getUniqueId())) return false;

        if (plugin.getConfig().getBoolean("uhc.lock-join-after-start", true)
                && phase == Phase.RUNNING) return false;

        return true;
    }

    @Override
    public String getJoinDenyMessageKey() {
        return "uhc-eliminated-no-rejoin";
    }

    @Override
    public boolean onPlayerDeath(Player player) {
        if (eventWorld == null) return false;
        if (player.getWorld() != eventWorld) return false;

        eliminated.add(player.getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            player.getInventory().clear();
            player.setHealth(20.0);
            player.setFoodLevel(20);

            player.setGameMode(GameMode.SPECTATOR);

            Location lobby = plugin.getConfig().getBoolean("uhc.waiting-box.enabled", true)
                    ? getWaitingBoxSpawn()
                    : new Location(eventWorld, 0.5, 66, 0.5);

            TeleportUtil.teleportRetainingRideStack(plugin, player, lobby);
            player.sendMessage(plugin.getMessage("uhc-eliminated"));
        }, 1L);

        if (phase == Phase.RUNNING) {
            Bukkit.getScheduler().runTaskLater(plugin, this::checkForWinner, 2L);
        }

        return true;
    }

    @Override
    public String getEventType() {
        return "UHC";
    }


    private void saveReturnLocation(Player player) {
        UUID uuid = player.getUniqueId();
        returnLocations.putIfAbsent(uuid, player.getLocation().clone());
    }

    private void restoreReturnLocation(Player player) {
        UUID uuid = player.getUniqueId();
        Location back = returnLocations.remove(uuid);
        if (back == null) return;

        if (back.getWorld() == null) {
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (w != null) back = w.getSpawnLocation();
        }

        if (back == null) return;
        TeleportUtil.teleportRetainingRideStack(plugin, player, back);
    }


    public boolean isWaitingPhase() {
        return active && phase == Phase.CREATED;
    }

    public boolean isInWaitingBox(Location loc) {
        if (eventWorld == null) return false;
        if (loc == null || loc.getWorld() != eventWorld) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        return x >= boxMinX && x <= boxMaxX
                && y >= boxMinY && y <= boxMaxY
                && z >= boxMinZ && z <= boxMaxZ;
    }

    private Location getWaitingBoxSpawn() {
        double x = plugin.getConfig().getDouble("uhc.waiting-box.center-x", 0);
        double y = plugin.getConfig().getDouble("uhc.waiting-box.center-y", 150);
        double z = plugin.getConfig().getDouble("uhc.waiting-box.center-z", 0);
        return new Location(eventWorld, x + 0.5, y + 1, z + 0.5);
    }

    private void buildWaitingBox() {
        int cx = plugin.getConfig().getInt("uhc.waiting-box.center-x", 0);
        int cy = plugin.getConfig().getInt("uhc.waiting-box.center-y", 150);
        int cz = plugin.getConfig().getInt("uhc.waiting-box.center-z", 0);
        int r = plugin.getConfig().getInt("uhc.waiting-box.radius", 6);
        int h = plugin.getConfig().getInt("uhc.waiting-box.height", 5);

        Material wall = Material.matchMaterial(plugin.getConfig().getString("uhc.waiting-box.wall-material", "GLASS"));
        Material floor = Material.matchMaterial(plugin.getConfig().getString("uhc.waiting-box.floor-material", "WHITE_CONCRETE"));
        Material roof = Material.matchMaterial(plugin.getConfig().getString("uhc.waiting-box.roof-material", "GLASS"));

        if (wall == null) wall = Material.GLASS;
        if (floor == null) floor = Material.WHITE_CONCRETE;
        if (roof == null) roof = Material.GLASS;

        int minX = cx - r;
        int maxX = cx + r;
        int minZ = cz - r;
        int maxZ = cz + r;
        int minY = cy;
        int maxY = cy + h;

        boxMinX = minX;
        boxMaxX = maxX;
        boxMinZ = minZ;
        boxMaxZ = maxZ;
        boxMinY = minY;
        boxMaxY = maxY + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                eventWorld.getBlockAt(x, minY, z).setType(floor, false);
            }
        }

        for (int y = minY + 1; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean edge = (x == minX || x == maxX || z == minZ || z == maxZ);
                    if (edge) eventWorld.getBlockAt(x, y, z).setType(wall, false);
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                eventWorld.getBlockAt(x, maxY + 1, z).setType(roof, false);
            }
        }
    }


    private void prepMatchPlayer(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            giveStarterKit(player);
        });
    }

    private void giveStarterKit(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        player.getInventory().addItem(new ItemStack(Material.BREAD, 16));
        player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 32));
    }

    private void scatterPlayer(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            int borderSize = plugin.getConfig().getInt("uhc.world-border-size", 1000);
            int half = borderSize / 2;

            int margin = 50;
            int min = -half + margin;
            int max = half - margin;

            for (int tries = 0; tries < 40; tries++) {
                int x = ThreadLocalRandom.current().nextInt(min, max + 1);
                int z = ThreadLocalRandom.current().nextInt(min, max + 1);

                int y = eventWorld.getHighestBlockYAt(x, z) + 1;
                Location loc = new Location(eventWorld, x + 0.5, y, z + 0.5);

                if (isSafeSpawn(loc)) {
                    plugin.getLogger().info("UHC DEBUG: scattering " + player.getName() + " to " + fmt(loc));
                    TeleportUtil.teleportRetainingRideStack(plugin, player, loc);
                    return;
                }
            }

            Location fallback = new Location(eventWorld, 0.5, eventWorld.getHighestBlockYAt(0, 0) + 1, 0.5);
            plugin.getLogger().warning("UHC DEBUG: scatter fallback for " + player.getName() + " -> " + fmt(fallback));
            TeleportUtil.teleportRetainingRideStack(plugin, player, fallback);
        });
    }

    private boolean isSafeSpawn(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block below = loc.clone().add(0, -1, 0).getBlock();

        if (!feet.getType().isAir()) return false;
        if (!head.getType().isAir()) return false;

        Material m = below.getType();
        if (m == Material.LAVA || m == Material.WATER) return false;
        return m.isSolid();
    }

    private void checkForWinner() {
        List<Player> alive = new ArrayList<>();
        for (UUID uuid : plugin.getEventManager().getEventPlayerUUIDs()) {
            if (eliminated.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getWorld() != eventWorld) continue;
            if (p.isDead()) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            alive.add(p);
        }

        if (alive.size() == 1) {
            Player winner = alive.get(0);
            Bukkit.broadcastMessage(plugin.getMessage("uhc-winner").replace("{player}", winner.getName()));
            plugin.getEventManager().endEvent();
        }
    }

    private String fmt(Location l) {
        if (l == null || l.getWorld() == null) return "null";
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}