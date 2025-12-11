package com.flyaway.warpbonus;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class WarpBonusManager {
    private final WarpBonusPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private LuckPerms luckPerms;

    public WarpBonusManager(WarpBonusPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "bonus_warps.yml");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        loadData();

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
        }
    }

    private void loadData() {
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            saveData();
        } else {
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        }
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить bonus_warps.yml", e);
        }
    }

    public int getBonusWarps(UUID playerId) {
        loadData();
        return dataConfig.getInt(playerId.toString(), 0);
    }

    public void setBonusWarps(UUID playerId, int amount) {
        loadData();
        if (amount > 0) {
            dataConfig.set(playerId.toString(), amount);
        } else {
            dataConfig.set(playerId.toString(), null);
        }
        saveData();
    }

    public void addBonusWarp(UUID playerId) {
        int current = getBonusWarps(playerId);
        int newAmount = current + 1;
        setBonusWarps(playerId, newAmount);
        plugin.getLogger().info("Добавлен бонусный варп игроку " + playerId + ". Теперь бонусов: " + newAmount);
    }

    public void updatePlayerPermissions(UUID playerId, String playerName) {
        if (luckPerms == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                User user = getUser(playerId);
                if (user == null) {
                    plugin.getLogger().warning("Не удалось получить пользователя: " + playerName);
                    return;
                }

                int groupLimit = getGroupWarpLimit(user);
                int bonus = getBonusWarps(playerId);
                int totalWarps = groupLimit + bonus;

                removeOldWarpPermissions(user);

                String permission = "axplayerwarps.warps." + totalWarps;
                user.data().add(Node.builder(permission).value(true).build());

                luckPerms.getUserManager().saveUser(user);

                plugin.getLogger().info("Обновлены права для " + playerName +
                        " (группа: " + groupLimit + ", бонусы: " + bonus + ", итого: " + totalWarps + ")");

                updateOnlinePlayer(playerId);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при обновлении прав игрока " + playerName, e);
            }
        });
    }

    private User getUser(UUID playerId) {
        UserManager userManager = luckPerms.getUserManager();
        User user = userManager.getUser(playerId);
        if (user == null) {
            user = userManager.loadUser(playerId).join();
        }
        return user;
    }

    private int getGroupWarpLimit(User user) {
        int maxLimit = 0;
        String primaryGroup = user.getPrimaryGroup();
        GroupManager groupManager = luckPerms.getGroupManager();
        Group group = groupManager.getGroup(primaryGroup);

        if (group != null) {
            maxLimit = Math.max(maxLimit, getWarpLimitFromNodes(group.getNodes()));
        }

        Collection<Group> inheritedGroups = user.getInheritedGroups(QueryOptions.nonContextual());
        for (Group inheritedGroup : inheritedGroups) {
            maxLimit = Math.max(maxLimit, getWarpLimitFromNodes(inheritedGroup.getNodes()));
        }

        return maxLimit;
    }

    private int getWarpLimitFromNodes(Collection<Node> nodes) {
        int limit = 0;
        for (Node node : nodes) {
            String key = node.getKey();
            if (key.startsWith("axplayerwarps.warps.") && node.getValue()) {
                try {
                    limit = Math.max(limit, Integer.parseInt(key.substring("axplayerwarps.warps.".length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return limit;
    }

    private void removeOldWarpPermissions(User user) {
        Set<Node> toRemove = new HashSet<>();
        for (Node node : user.getNodes()) {
            if (node.getKey().startsWith("axplayerwarps.warps.")) {
                toRemove.add(node);
            }
        }
        for (Node node : toRemove) {
            user.data().remove(node);
        }
    }

    private void updateOnlinePlayer(UUID playerId) {
        if (luckPerms == null) return;

        luckPerms.getUserManager().loadUser(playerId).thenAcceptAsync(user -> {
            if (user != null) {
                luckPerms.getUserManager().cleanupUser(user);
            }
        });
    }

    public String getPlayerName(UUID playerId) {
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) return onlinePlayer.getName();

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : playerId.toString();
    }

    public Map<String, Integer> getReadableBonusWarps() {
        loadData();
        Map<String, Integer> result = new HashMap<>();
        for (String key : dataConfig.getKeys(false)) {
            UUID playerId = UUID.fromString(key);
            int bonus = dataConfig.getInt(key);
            String playerName = getPlayerName(playerId);
            result.put(playerName, bonus);
        }
        return result;
    }
}
