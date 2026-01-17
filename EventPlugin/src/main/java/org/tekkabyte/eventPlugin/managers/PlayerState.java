package org.tekkabyte.eventPlugin.managers;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerState {
    private final Location location;
    private final GameMode gameMode;
    private final double health;
    private final int food;
    private final ItemStack[] contents;
    private final ItemStack[] armor;

    public PlayerState(Player p) {
        this.location = p.getLocation().clone();
        this.gameMode = p.getGameMode();
        this.health = Math.max(1.0, p.getHealth());
        this.food = p.getFoodLevel();
        this.contents = p.getInventory().getContents();
        this.armor = p.getInventory().getArmorContents();
    }

    public void restore(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(contents);
        p.getInventory().setArmorContents(armor);
        p.setGameMode(gameMode);
        p.setHealth(Math.min(20.0, health));
        p.setFoodLevel(food);
        p.teleport(location);
    }
}
