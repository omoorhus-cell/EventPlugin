package org.tekkabyte.eventPlugin.models;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.*;
import java.util.Base64;

public class CustomRecipe {

    private final String id;
    private final String name;
    private final ItemStack result;
    private final List<ItemStack> materials;

    private int maxCrafts;
    private int craftsUsed;

    public CustomRecipe(String id, String name, ItemStack result, List<ItemStack> materials) {
        this(id, name, result, materials, -1, 0);
    }

    public CustomRecipe(String id, String name, ItemStack result, List<ItemStack> materials, int maxCrafts, int craftsUsed) {
        this.id = id;
        this.name = name;
        this.result = result;
        this.materials = materials == null ? new ArrayList<>() : new ArrayList<>(materials);
        this.maxCrafts = maxCrafts;
        this.craftsUsed = craftsUsed;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ItemStack getResult() { return result; }
    public List<ItemStack> getMaterials() { return new ArrayList<>(materials); }

    public int getMaxCrafts() { return maxCrafts; }
    public int getCraftsUsed() { return craftsUsed; }

    public void setMaxCrafts(int maxCrafts) { this.maxCrafts = maxCrafts; }
    public void setCraftsUsed(int craftsUsed) { this.craftsUsed = craftsUsed; }

    public boolean isUnlimited() { return maxCrafts < 0; }

    public boolean canCraftMore() {
        return isUnlimited() || craftsUsed < maxCrafts;
    }

    public int remainingCrafts() {
        if (isUnlimited()) return -1;
        return Math.max(0, maxCrafts - craftsUsed);
    }

    public void incrementCraftsUsed(int amount) {
        if (amount <= 0) return;
        this.craftsUsed += amount;
        if (!isUnlimited() && this.craftsUsed > this.maxCrafts) this.craftsUsed = this.maxCrafts;
    }

    public String serializeResult() {
        return serializeItemStack(result);
    }

    public String serializeMaterials() {
        if (materials == null || materials.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < materials.size(); i++) {
            ItemStack it = materials.get(i);
            if (it == null) continue;
            if (sb.length() > 0) sb.append("|");
            sb.append(serializeItemStack(it));
        }
        return sb.toString();
    }

    public static ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object obj = in.readObject();
                return (ItemStack) obj;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<ItemStack> deserializeMaterials(String data) {
        List<ItemStack> out = new ArrayList<>();
        if (data == null || data.isEmpty()) return out;
        String[] parts = data.split("\\|");
        for (String p : parts) {
            ItemStack it = deserializeItemStack(p);
            if (it != null) out.add(it);
        }
        return out;
    }

    private static String serializeItemStack(ItemStack item) {
        if (item == null) return "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Throwable t) {
            return "";
        }
    }
}