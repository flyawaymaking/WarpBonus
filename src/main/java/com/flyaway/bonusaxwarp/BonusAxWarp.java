package com.flyaway.bonusaxwarp;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BonusAxWarp extends JavaPlugin {
    private GroupChangeListener groupChangeListener;
    private static BonusAxWarp instance;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().severe("LuckPerms not found! Plugin disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        BonusWarpManager bonusManager = new BonusWarpManager(this);

        getCommand("bonusaxwarp").setExecutor(new BonusAxWarpCommand(bonusManager));
        getCommand("bonusaxwarp").setTabCompleter(new BonusAxWarpCommand(bonusManager));

        this.groupChangeListener = new GroupChangeListener(bonusManager);
        Bukkit.getPluginManager().registerEvents(groupChangeListener, this);

        getLogger().info("BonusAxWarp enabled!");
    }

    @Override
    public void onDisable() {
        if (groupChangeListener != null) {
            groupChangeListener.disable();
        }
        getLogger().info("BonusAxWarp disabled!");
    }

    public static BonusAxWarp getInstance() {
        return instance;
    }
}
