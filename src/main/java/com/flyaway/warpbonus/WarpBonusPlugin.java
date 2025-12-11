package com.flyaway.warpbonus;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class WarpBonusPlugin extends JavaPlugin {
    private WarpBonusManager bonusManager;
    private GroupChangeListener groupChangeListener;
    private static WarpBonusPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().severe("LuckPerms не найден! Плагин будет отключен.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.bonusManager = new WarpBonusManager(this);

        getCommand("warpbonus").setExecutor(new WarpBonusCommand(bonusManager));
        getCommand("warpbonus").setTabCompleter(new WarpBonusCommand(bonusManager));

        this.groupChangeListener = new GroupChangeListener(bonusManager);
        Bukkit.getPluginManager().registerEvents(groupChangeListener, this);

        getLogger().info("Плагин WarpBonus успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (groupChangeListener != null) {
            groupChangeListener.disable();
        }
        getLogger().info("Плагин WarpBonus отключен!");
    }

    public static WarpBonusPlugin getInstance() {
        return instance;
    }

    public WarpBonusManager getBonusManager() {
        return bonusManager;
    }
}
