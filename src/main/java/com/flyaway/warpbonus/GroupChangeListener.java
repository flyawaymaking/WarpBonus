package com.flyaway.warpbonus;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.track.UserPromoteEvent;
import net.luckperms.api.event.user.track.UserDemoteEvent;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GroupChangeListener implements Listener {
    private final WarpBonusManager bonusManager;
    private final List<EventSubscription<?>> subscriptions = new ArrayList<>();

    public GroupChangeListener(WarpBonusManager bonusManager) {
        this.bonusManager = bonusManager;
        registerLuckPermsEvents();
    }

    private void registerLuckPermsEvents() {
        try {
            LuckPerms luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
            if (luckPerms != null) {
                EventBus eventBus = luckPerms.getEventBus();

                subscriptions.add(eventBus.subscribe(UserPromoteEvent.class, this::onUserPromote));
                subscriptions.add(eventBus.subscribe(UserDemoteEvent.class, this::onUserDemote));
                subscriptions.add(eventBus.subscribe(NodeAddEvent.class, this::onNodeAdd));
                subscriptions.add(eventBus.subscribe(NodeRemoveEvent.class, this::onNodeRemove));

                WarpBonusPlugin.getInstance().getLogger().info("LuckPerms события зарегистрированы");
            }
        } catch (Exception e) {
            WarpBonusPlugin.getInstance().getLogger().warning("Не удалось зарегистрировать LuckPerms события: " + e.getMessage());
        }
    }

    public void disable() {
        for (EventSubscription<?> sub : subscriptions) {
            try {
                sub.close();
            } catch (Exception ignored) {}
        }
        subscriptions.clear();
        WarpBonusPlugin.getInstance().getLogger().info("GroupChangeListener отключен");
    }

    private void onUserPromote(UserPromoteEvent event) {
        schedulePermissionUpdate(event.getUser());
    }

    private void onUserDemote(UserDemoteEvent event) {
        schedulePermissionUpdate(event.getUser());
    }

    private void onNodeAdd(NodeAddEvent event) {
        // Фильтруем: только добавление родительских групп пользователю
        if (event.getTarget() instanceof User user && event.getNode().getType() == NodeType.INHERITANCE) {
            schedulePermissionUpdate(user);
        }

        // Фильтруем: только изменения варп-пермишенов в группах (НЕ у пользователей!)
        if (event.getNode().getKey().startsWith("axplayerwarps.warps.") &&
                event.getTarget() instanceof net.luckperms.api.model.group.Group group) {
            updateAllUsersInGroup(group.getName());
        }
    }

    private void onNodeRemove(NodeRemoveEvent event) {
        // Фильтруем: только удаление родительских групп у пользователя
        if (event.getTarget() instanceof User user && event.getNode().getType() == NodeType.INHERITANCE) {
            schedulePermissionUpdate(user);
        }

        // Фильтруем: только изменения варп-пермишенов в группах (НЕ у пользователей!)
        if (event.getNode().getKey().startsWith("axplayerwarps.warps.") &&
                event.getTarget() instanceof net.luckperms.api.model.group.Group group) {
            updateAllUsersInGroup(group.getName());
        }
    }

    private void updateAllUsersInGroup(String groupName) {
        Bukkit.getScheduler().runTaskAsynchronously(WarpBonusPlugin.getInstance(), () -> {
            try {
                net.luckperms.api.LuckPerms luckPerms = Bukkit.getServicesManager().load(net.luckperms.api.LuckPerms.class);
                if (luckPerms == null) return;

                int updatedCount = 0;
                for (User user : luckPerms.getUserManager().getLoadedUsers()) {
                    if (user.getPrimaryGroup().equals(groupName) ||
                        user.getInheritedGroups(net.luckperms.api.query.QueryOptions.nonContextual())
                            .stream()
                            .anyMatch(group -> group.getName().equals(groupName))) {

                        UUID playerId = user.getUniqueId();
                        String playerName = user.getUsername();

                        if (playerName != null && !playerName.isEmpty()) {
                            Bukkit.getScheduler().runTask(WarpBonusPlugin.getInstance(), () -> {
                                bonusManager.updatePlayerPermissions(playerId, playerName);
                            });
                            updatedCount++;
                        }
                    }
                }

                if (updatedCount > 0) {
                    WarpBonusPlugin.getInstance().getLogger().info("Обновлены варпы для " + updatedCount + " пользователей группы " + groupName);
                }

            } catch (Exception e) {
                WarpBonusPlugin.getInstance().getLogger().warning("Ошибка при обновлении пользователей группы " + groupName + ": " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Обновляем права при входе игрока
        Bukkit.getScheduler().runTaskLater(WarpBonusPlugin.getInstance(), () -> {
            UUID playerId = event.getPlayer().getUniqueId();
            String playerName = event.getPlayer().getName();
            bonusManager.updatePlayerPermissions(playerId, playerName);
        }, 20L);
    }

    private void schedulePermissionUpdate(User user) {
        Bukkit.getScheduler().runTaskLater(WarpBonusPlugin.getInstance(), () -> {
            UUID playerId = user.getUniqueId();
            String playerName = user.getUsername();

            if (playerName != null && !playerName.isEmpty()) {
                bonusManager.updatePlayerPermissions(playerId, playerName);
            }
        }, 20L);
    }
}
