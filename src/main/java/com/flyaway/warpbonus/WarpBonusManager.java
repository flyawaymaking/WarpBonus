package com.flyaway.warpbonus;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.*;
import java.util.*;
import java.util.logging.Level;

public class WarpBonusManager {
    private final WarpBonusPlugin plugin;
    private final Map<UUID, Integer> bonusWarps = new HashMap<>();
    private final File dataFile;
    private LuckPerms luckPerms;

    public WarpBonusManager(WarpBonusPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "bonus_warps.dat");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
        }
    }

    public void addBonusWarp(UUID playerId, String playerName) {
        int currentBonus = getBonusWarps(playerId);
        int newBonus = currentBonus + 1;
        bonusWarps.put(playerId, newBonus);

        updatePlayerPermissions(playerId, playerName);
        saveBonusData();

        plugin.getLogger().info("Добавлен бонусный варп игроку " + playerName + ". Теперь бонусов: " + newBonus);
    }

    public int getBonusWarps(UUID playerId) {
        return bonusWarps.getOrDefault(playerId, 0);
    }

    public void updatePlayerPermissions(UUID playerId, String playerName) {
        if (luckPerms == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                User user = getUser(playerId, playerName);
                if (user == null) {
                    plugin.getLogger().warning("Не удалось получить пользователя: " + playerName);
                    return;
                }

                // ВАЖНО: получаем ТОЛЬКО групповой лимит, игнорируя пермишены самого игрока
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

    private User getUser(UUID playerId, String playerName) {
        UserManager userManager = luckPerms.getUserManager();
        User user = userManager.getUser(playerId);

        if (user == null) {
            user = userManager.loadUser(playerId).join();
        }

        return user;
    }

    private int getGroupWarpLimit(User user) {
        int maxLimit = 0;

        // Получаем лимит из первичной группы
        String primaryGroup = user.getPrimaryGroup();
        GroupManager groupManager = luckPerms.getGroupManager();
        Group group = groupManager.getGroup(primaryGroup);

        if (group != null) {
            int groupLimit = getWarpLimitFromNodes(group.getNodes());
            maxLimit = Math.max(maxLimit, groupLimit);
        }

        // Получаем лимиты из всех унаследованных групп
        Collection<Group> inheritedGroups = user.getInheritedGroups(QueryOptions.nonContextual());
        for (Group inheritedGroup : inheritedGroups) {
            int groupLimit = getWarpLimitFromNodes(inheritedGroup.getNodes());
            maxLimit = Math.max(maxLimit, groupLimit);
        }

        // ВАЖНО: НЕ смотрим на пермишены самого игрока!
        // Это предотвращает цикл и неправильный расчет
        // int playerLimit = getWarpLimitFromNodes(user.getNodes()); // УБРАНО!
        // maxLimit = Math.max(maxLimit, playerLimit); // УБРАНО!

        return maxLimit;
    }

    private int getWarpLimitFromNodes(Collection<Node> nodes) {
        int limit = 0;
        for (Node node : nodes) {
            String key = node.getKey();
            if (key.startsWith("axplayerwarps.warps.") && node.getValue()) {
                try {
                    int warpLimit = Integer.parseInt(key.substring("axplayerwarps.warps.".length()));
                    limit = Math.max(limit, warpLimit);
                } catch (NumberFormatException ignored) {}
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
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                luckPerms.getUserManager().cleanupUser(luckPerms.getUserManager().getUser(playerId));
            }
        });
    }

    public void loadBonusData() {
        if (!dataFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        UUID playerId = UUID.fromString(parts[0]);
                        int bonus = Integer.parseInt(parts[1]);
                        bonusWarps.put(playerId, bonus);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Некорректная строка в файле данных: " + line);
                    }
                }
            }
            plugin.getLogger().info("Загружено " + bonusWarps.size() + " записей бонусных варпов");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки данных бонусных варпов", e);
        }
    }

    public void saveBonusData() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
            for (Map.Entry<UUID, Integer> entry : bonusWarps.entrySet()) {
                writer.write(entry.getKey().toString() + ":" + entry.getValue());
                writer.newLine();
            }
            plugin.getLogger().info("Сохранено " + bonusWarps.size() + " записей бонусных варпов");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения данных бонусных варпов", e);
        }
    }

    public Map<UUID, Integer> getAllBonusWarps() {
        return new HashMap<>(bonusWarps);
    }

    public void setBonusWarps(UUID playerId, String playerName, int amount) {
        bonusWarps.put(playerId, amount);
        updatePlayerPermissions(playerId, playerName);
        saveBonusData();
    }

    public String getPlayerName(UUID playerId) {
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        return playerId.toString();
    }

    public Map<String, Integer> getReadableBonusWarps() {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : bonusWarps.entrySet()) {
            String playerName = getPlayerName(entry.getKey());
            result.put(playerName, entry.getValue());
        }
        return result;
    }
}
