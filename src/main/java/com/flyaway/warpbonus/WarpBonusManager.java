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

    private int getBonusWarpsFromFile(UUID playerId) {
        if (!dataFile.exists()) return 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].equals(playerId.toString())) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка чтения данных для " + playerId, e);
        }
        return 0;
    }

    private void saveBonusWarpsToFile(UUID playerId, int amount) {
        // Создаем временный файл для безопасной записи
        File tempFile = new File(dataFile.getParentFile(), "bonus_warps.tmp");
        Map<UUID, Integer> allData = new HashMap<>();

        // Читаем существующие данные
        if (dataFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        allData.put(UUID.fromString(parts[0]), Integer.parseInt(parts[1]));
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка чтения файла", e);
            }
        }

        // Обновляем данные
        if (amount > 0) {
            allData.put(playerId, amount);
        } else {
            allData.remove(playerId); // Удаляем если 0
        }

        // Записываем обратно
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            for (Map.Entry<UUID, Integer> entry : allData.entrySet()) {
                writer.write(entry.getKey().toString() + ":" + entry.getValue());
                writer.newLine();
            }

            // Заменяем старый файл новым
            if (dataFile.exists()) {
                dataFile.delete();
            }
            tempFile.renameTo(dataFile);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения данных", e);
            tempFile.delete();
        }
    }

    public void addBonusWarp(UUID playerId, String playerName) {
        int currentBonus = getBonusWarps(playerId);
        int newBonus = currentBonus + 1;
        saveBonusWarpsToFile(playerId, newBonus);

        updatePlayerPermissions(playerId, playerName);

        plugin.getLogger().info("Добавлен бонусный варп игроку " + playerName + ". Теперь бонусов: " + newBonus);
    }

    public int getBonusWarps(UUID playerId) {
        return getBonusWarpsFromFile(playerId);
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

    public void setBonusWarps(UUID playerId, String playerName, int amount) {
        saveBonusWarpsToFile(playerId, amount);
        updatePlayerPermissions(playerId, playerName);
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
        if (!dataFile.exists()) return result;

        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    UUID playerId = UUID.fromString(parts[0]);
                    int bonus = Integer.parseInt(parts[1]);
                    String playerName = getPlayerName(playerId);
                    result.put(playerName, bonus);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка чтения списка варпов", e);
        }
        return result;
    }
}
