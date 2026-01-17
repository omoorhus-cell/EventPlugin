package org.tekkabyte.eventPlugin.database;

import org.bukkit.inventory.ItemStack;
import org.tekkabyte.eventPlugin.EventPlugin;
import org.tekkabyte.eventPlugin.models.CustomRecipe;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private final EventPlugin plugin;

    private Connection connection;
    private volatile boolean connected = false;

    public DatabaseManager(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            String fileName = plugin.getConfig().getString("database.sqlite-file", "data.db");
            File dbFile = new File(plugin.getDataFolder(), fileName);

            Class.forName("org.sqlite.JDBC");

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS recipes (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      result TEXT NOT NULL,
                      materials TEXT NOT NULL
                    );
                """);
            }

            ensureColumns();

            connected = true;
            plugin.getLogger().info("[Database] Using SQLite at " + dbFile.getAbsolutePath());
        } catch (Throwable t) {
            connected = false;
            plugin.getLogger().severe("[Database] Failed to initialize SQLite. Recipes will NOT persist!");
            t.printStackTrace();
        }
    }

    private void ensureColumns() {
        if (connection == null) return;

        boolean hasMaxUses = false;
        boolean hasUses = false;

        try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(recipes);");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if ("max_uses".equalsIgnoreCase(name)) hasMaxUses = true;
                if ("uses".equalsIgnoreCase(name)) hasUses = true;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to inspect recipes table.");
            e.printStackTrace();
        }

        try (Statement st = connection.createStatement()) {
            if (!hasMaxUses) st.execute("ALTER TABLE recipes ADD COLUMN max_uses INTEGER NOT NULL DEFAULT -1;");
            if (!hasUses) st.execute("ALTER TABLE recipes ADD COLUMN uses INTEGER NOT NULL DEFAULT 0;");
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to migrate recipes table.");
            e.printStackTrace();
        }
    }

    public void saveRecipe(CustomRecipe recipe) {
        if (!isConnected() || recipe == null) return;

        String id = recipe.getId();
        String name = recipe.getName() == null ? "Recipe" : recipe.getName();

        String result = recipe.serializeResult();
        String materials = recipe.serializeMaterials();

        int maxUses = recipe.getMaxCrafts();
        int uses = recipe.getCraftsUsed();

        String sql = """
            INSERT INTO recipes (id, name, result, materials, max_uses, uses)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name,
              result=excluded.result,
              materials=excluded.materials,
              max_uses=excluded.max_uses,
              uses=excluded.uses;
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, result);
            ps.setString(4, materials);
            ps.setInt(5, maxUses);
            ps.setInt(6, uses);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to save recipe: " + id);
            e.printStackTrace();
        }
    }

    public List<CustomRecipe> loadAllRecipes() {
        List<CustomRecipe> out = new ArrayList<>();
        if (!isConnected()) return out;

        String sql = "SELECT id, name, result, materials, max_uses, uses FROM recipes;";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String resultStr = rs.getString("result");
                String matsStr = rs.getString("materials");

                int maxUses = rs.getInt("max_uses");
                int uses = rs.getInt("uses");

                ItemStack result = CustomRecipe.deserializeItemStack(resultStr);
                List<ItemStack> materials = CustomRecipe.deserializeMaterials(matsStr);

                out.add(new CustomRecipe(id, name, result, materials, maxUses, uses));
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to load recipes.");
            e.printStackTrace();
        }

        return out;
    }

    public void deleteRecipe(String id) {
        if (!isConnected() || id == null || id.isEmpty()) return;

        String sql = "DELETE FROM recipes WHERE id = ?;";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to delete recipe: " + id);
            e.printStackTrace();
        }
    }

    public void close() {
        connected = false;

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
        connection = null;
    }

    public boolean isConnected() {
        try {
            return connected && connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}