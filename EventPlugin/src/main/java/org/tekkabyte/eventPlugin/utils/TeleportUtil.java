package org.tekkabyte.eventPlugin.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class TeleportUtil {

    private TeleportUtil() {}

    public static void teleportRetainingRideStack(JavaPlugin plugin, Player player, Location dest) {
        if (plugin == null || player == null || dest == null) return;

        Entity root = getTopVehicle(player);
        RideNode tree = captureRideTree(root);

        detachRideTree(tree);
        root.teleport(dest);

        plugin.getServer().getScheduler().runTask(plugin, () -> reattachRideTree(tree));
    }

    private static Entity getTopVehicle(Entity e) {
        Entity cur = e;
        while (cur.getVehicle() != null) cur = cur.getVehicle();
        return cur;
    }

    private static RideNode captureRideTree(Entity root) {
        RideNode node = new RideNode(root);
        for (Entity passenger : new ArrayList<>(root.getPassengers())) {
            node.passengers.add(captureRideTree(passenger));
        }
        return node;
    }

    private static void detachRideTree(RideNode node) {
        for (RideNode child : node.passengers) detachRideTree(child);
        node.entity.eject();
    }

    private static void reattachRideTree(RideNode node) {
        for (RideNode child : node.passengers) {
            if (!node.entity.isValid() || !child.entity.isValid()) continue;
            node.entity.addPassenger(child.entity);
            reattachRideTree(child);
        }
    }

    private static final class RideNode {
        private final Entity entity;
        private final List<RideNode> passengers = new ArrayList<>();
        private RideNode(Entity entity) { this.entity = entity; }
    }
}