package com.flyaway.warpbonus;

import net.luckperms.api.event.EventBus;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class GroupChangeListener implements Listener {
    private final WarpBonusManager bonusManager;
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public GroupChangeListener(WarpBonusManager bonusManager) {
        this.bonusManager = bonusManager;
        registerLuckPermsEvents();
    }

    private void registerLuckPermsEvents() {
        try {
            net.luckperms.api.LuckPerms luckPerms = Bukkit.getServicesManager().load(net.luckperms.api.LuckPerms.class);
            if (luckPerms != null) {
                EventBus eventBus = luckPerms.getEventBus();

                // Регистрируем обработчики событий
                eventBus.subscribe(UserPromoteEvent.class, this::onUserPromote);
                eventBus.subscribe(UserDemoteEvent.class, this::onUserDemote);
                eventBus.subscribe(NodeAddEvent.class, this::onNodeAdd);
                eventBus.subscribe(NodeRemoveEvent.class, this::onNodeRemove);

                WarpBonusPlugin.getInstance().getLogger().info("LuckPerms события зарегистрированы");
            }
        } catch (Exception e) {
            WarpBonusPlugin.getInstance().getLogger().warning("Не удалось зарегистрировать LuckPerms события: " + e.getMessage());
        }
    }

    public void disable() {
        enabled.set(false);
        WarpBonusPlugin.getInstance().getLogger().info("GroupChangeListener отключен");
    }

    private void onUserPromote(UserPromoteEvent event) {
        if (!enabled.get() || !WarpBonusPlugin.getInstance().isEnabled()) return;
        schedulePermissionUpdate(event.getUser());
    }

    private void onUserDemote(UserDemoteEvent event) {
        if (!enabled.get() || !WarpBonusPlugin.getInstance().isEnabled()) return;
        schedulePermissionUpdate(event.getUser());
    }

    private void onNodeAdd(NodeAddEvent event) {
        if (!enabled.get() || !WarpBonusPlugin.getInstance().isEnabled()) return;

        // Фильтруем: только добавление родительских групп пользователю
        if (event.getTarget() instanceof User && event.getNode().getType() == NodeType.INHERITANCE) {
            User user = (User) event.getTarget();
            schedulePermissionUpdate(user);
        }

        // Фильтруем: только изменения варп-пермишенов в группах (НЕ у пользователей!)
        if (event.getNode().getKey().startsWith("axplayerwarps.warps.") &&
            event.getTarget() instanceof net.luckperms.api.model.group.Group) {
            net.luckperms.api.model.group.Group group = (net.luckperms.api.model.group.Group) event.getTarget();
            updateAllUsersInGroup(group.getName());
        }
    }

    private void onNodeRemove(NodeRemoveEvent event) {
        if (!enabled.get() || !WarpBonusPlugin.getInstance().isEnabled()) return;

        // Фильтруем: только удаление родительских групп у пользователя
        if (event.getTarget() instanceof User && event.getNode().getType() == NodeType.INHERITANCE) {
            User user = (User) event.getTarget();
            schedulePermissionUpdate(user);
        }

        // Фильтруем: только изменения варп-пермишенов в группах (НЕ у пользователей!)
        if (event.getNode().getKey().startsWith("axplayerwarps.warps.") &&
            event.getTarget() instanceof net.luckperms.api.model.group.Group) {
            net.luckperms.api.model.group.Group group = (net.luckperms.api.model.group.Group) event.getTarget();
            updateAllUsersInGroup(group.getName());
        }
    }

    private void updateAllUsersInGroup(String groupName) {
        if (!enabled.get() || !WarpBonusPlugin.getInstance().isEnabled()) return;

        Bukkit.getScheduler().runTaskAsynchronously(WarpBonusPlugin.getInstance(), () -> {
            try {
                net.luckperms.api.LuckPerms luckPerms = Bukkit.getServicesManager().load(net.luckperms.api.LuckPerms.class);
                if (luckPerms == null) return;

                int updatedCount = 0;
                for (User user : luckPerms.getUserManager().getLoadedUsers()) {
                    if (!enabled.get()) return; // Проверяем еще раз перед обработкой каждого пользователя

                    if (user.getPrimaryGroup().equals(groupName) ||
                        user.getInheritedGroups(net.luckperms.api.query.QueryOptions.nonContextual())
                            .stream()
                            .anyMatch(group -> group.getName().equals(groupName))) {

                        UUID playerId = user.getUniqueId();
                        String playerName = user.getUsername();

                        if (playerName != null && !playerName.isEmpty() && enabled.get()) {
                            // Обновляем права пользователя
                            Bukkit.getScheduler().runTask(WarpBonusPlugin.getInstance(), () -> {
                                if (enabled.get()) {
                                    bonusManager.updatePlayerPermissions(playerId, playerName);
                                }
                            });
                            updatedCount++;
                        }
                    }
                }

                if (updatedCount > 0) {
                    WarpBonusPlugin.getInstance().getLogger().info("Обновлены варпы для " + updatedCount + " пользователей группы " + groupName);
                }

            } catch (Exception e) {
                if (enabled.get()) {
                    WarpBonusPlugin.getInstance().getLogger().warning("Ошибка при обновлении пользователей группы " + groupName + ": " + e.getMessage());
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled.get()) return;

        // Обновляем права при входе игрока
        Bukkit.getScheduler().runTaskLater(WarpBonusPlugin.getInstance(), () -> {
            if (enabled.get()) {
                UUID playerId = event.getPlayer().getUniqueId();
                String playerName = event.getPlayer().getName();
                bonusManager.updatePlayerPermissions(playerId, playerName);
            }
        }, 20L);
    }

    private void schedulePermissionUpdate(User user) {
        Bukkit.getScheduler().runTaskLater(WarpBonusPlugin.getInstance(), () -> {
            if (enabled.get()) {
                UUID playerId = user.getUniqueId();
                String playerName = user.getUsername();

                if (playerName != null && !playerName.isEmpty()) {
                    bonusManager.updatePlayerPermissions(playerId, playerName);
                }
            }
        }, 20L);
    }
}
