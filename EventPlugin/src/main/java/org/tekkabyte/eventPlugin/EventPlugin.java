package org.tekkabyte.eventPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.tekkabyte.eventPlugin.commands.*;
import org.tekkabyte.eventPlugin.database.DatabaseManager;
import org.tekkabyte.eventPlugin.gui.AdminGUI;
import org.tekkabyte.eventPlugin.gui.CraftingGUI;
import org.tekkabyte.eventPlugin.listeners.*;
import org.tekkabyte.eventPlugin.managers.EventManager;
import org.tekkabyte.eventPlugin.managers.RecipeManager;
import org.tekkabyte.eventPlugin.managers.WorldManager;

public class EventPlugin extends JavaPlugin {

    private static EventPlugin instance;

    private DatabaseManager databaseManager;
    private RecipeManager recipeManager;

    private EventManager eventManager;
    private WorldManager worldManager;

    private CraftingGUI craftingGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getLogger().info("Initializing EventCrafting + EventPlugin...");

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        recipeManager = new RecipeManager(this, databaseManager);
        recipeManager.loadRecipes();

        craftingGUI = new CraftingGUI(this, recipeManager);

        var craftCmd = getCommand("ecraft");
        if (craftCmd != null) craftCmd.setExecutor(new CraftCommand(this, recipeManager, craftingGUI));
        else getLogger().severe("Command 'ecraft' missing from plugin.yml!");

        var craftAdminCmd = getCommand("craftadmin");
        if (craftAdminCmd != null) craftAdminCmd.setExecutor(new CraftAdminCommand(this, recipeManager));
        else getLogger().severe("Command 'craftadmin' missing from plugin.yml!");

        var setRecipeCmd = getCommand("setrecipe");
        if (setRecipeCmd != null) setRecipeCmd.setExecutor(new SetRecipeCommand(this, recipeManager));
        else getLogger().severe("Command 'setrecipe' missing from plugin.yml!");

        AdminGUI adminGUI = new AdminGUI(this, recipeManager);
        getServer().getPluginManager().registerEvents(new AdminGUIListener(this, recipeManager, adminGUI), this);

        getServer().getPluginManager().registerEvents(new GUIListener(this, recipeManager, craftingGUI), this);

        worldManager = new WorldManager(this);
        eventManager = new EventManager(this);

        registerEventCommands();
        registerEventListeners();

        getLogger().info("EventCrafting + EventPlugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) eventManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("EventCrafting + EventPlugin disabled.");
    }

    private void registerEventCommands() {
        var eventCmd = getCommand("event");
        if (eventCmd != null) eventCmd.setExecutor(new EventCommand(this));
        else getLogger().severe("Command 'event' missing from plugin.yml!");

        var adminCmd = getCommand("eventadmin");
        if (adminCmd != null) adminCmd.setExecutor(new EventAdminCommand(this));
        else getLogger().severe("Command 'eventadmin' missing from plugin.yml!");
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new UHCWaitingBoxProtectListener(this), this);
    }

    public static EventPlugin getInstance() {
        return instance;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public CraftingGUI getCraftingGUI() {
        return craftingGUI;
    }

    public String getMessage(String path) {
        return getConfig()
                .getString("messages." + path, "Message not found")
                .replace("&", "§");
    }
}