package org.tekkabyte.eventPlugin.managers;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class WorldManager {
    private final JavaPlugin plugin;

    public WorldManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public World createEventWorld(String worldName, World.Environment env) {

        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) return loaded;

        WorldCreator wc = new WorldCreator(worldName);
        wc.environment(env);
        wc.type(WorldType.NORMAL);

        World world = wc.createWorld();
        if (world != null) {
            world.setDifficulty(Difficulty.HARD);
            world.setPVP(true);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(6000);
        }
        return world;
    }

    public World createTournamentWorld(String worldName) {
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) return loaded;

        WorldCreator wc = new WorldCreator(worldName);
        wc.environment(World.Environment.NORMAL);
        wc.type(WorldType.FLAT);


        World world = wc.createWorld();
        if (world != null) {
            world.setDifficulty(Difficulty.NORMAL);
            world.setPVP(true);
        }
        return world;
    }

    public void setupWorldBorder(World world, double size, double centerX, double centerZ) {
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setWarningDistance(50);
        border.setDamageAmount(0.2);
    }

    public void shrinkWorldBorder(World world, double finalSize, long timeSeconds) {
        if (world == null) return;
        world.getWorldBorder().setSize(finalSize, timeSeconds);
    }

    public void deleteWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            deleteWorldFolder(worldName);
            return;
        }

        World main = Bukkit.getWorlds().get(0);
        Location spawn = main.getSpawnLocation();
        for (Player p : world.getPlayers()) {
            p.teleport(spawn, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND);
        }

        Bukkit.unloadWorld(world, false);
        deleteWorldFolder(worldName);
    }

    private void deleteWorldFolder(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldFolder.exists()) return;
        deleteDirectory(worldFolder);
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
