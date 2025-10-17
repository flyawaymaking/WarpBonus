package com.flyaway.warpbonus;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class WarpBonusCommand implements CommandExecutor, TabCompleter {
    private final WarpBonusManager bonusManager;

    public WarpBonusCommand(WarpBonusManager bonusManager) {
        this.bonusManager = bonusManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("warps.bonus.manage")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /warpbonus add <игрок> [количество]");
                    return true;
                }
                handleAdd(sender, args);
                break;

            case "set":
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /warpbonus set <игрок> <количество>");
                    return true;
                }
                handleSet(sender, args);
                break;

            case "check":
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /warpbonus check <игрок>");
                    return true;
                }
                handleCheck(sender, args);
                break;

            case "list":
                handleList(sender);
                break;

            default:
                showUsage(sender);
                break;
        }

        return true;
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage("§6=== WarpBonus Команды ===");
        sender.sendMessage("§e/warpbonus add <игрок> [количество] §7- Добавить бонусные варпы");
        sender.sendMessage("§e/warpbonus set <игрок> <количество> §7- Установить количество бонусных варпов");
        sender.sendMessage("§e/warpbonus check <игрок> §7- Проверить бонусные варпы игрока");
        sender.sendMessage("§e/warpbonus list §7- Список всех бонусных варпов");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        String playerName = args[1];

        Bukkit.getScheduler().runTaskAsynchronously(WarpBonusPlugin.getInstance(), () -> {
            UUID playerId = getUUIDFromName(playerName);
            if (playerId == null) {
                sender.sendMessage("§cИгрок " + playerName + " не найден!");
                return;
            }

            if (args.length >= 3) {
                // Добавить указанное количество
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount <= 0) {
                        sender.sendMessage("§cКоличество должно быть положительным!");
                        return;
                    }
                    int currentBonus = bonusManager.getBonusWarps(playerId);
                    int newBonus = currentBonus + amount;
                    bonusManager.setBonusWarps(playerId, playerName, newBonus);

                    String currentPlayerName = bonusManager.getPlayerName(playerId);
                    sender.sendMessage("§aДобавлено §e" + amount + "§a бонусных варпов игроку " + currentPlayerName + ". Теперь: §e" + newBonus);

                    Player target = Bukkit.getPlayer(playerId);
                    if (target != null) {
                        target.sendMessage("§eВам было добавлено §6" + amount + "§e бонусных варпов! Всего: §6" + newBonus);
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНекорректное число!");
                }
            } else {
                // Добавить 1 варп по умолчанию
                bonusManager.addBonusWarp(playerId, playerName);
                int newBonus = bonusManager.getBonusWarps(playerId);
                String currentPlayerName = bonusManager.getPlayerName(playerId);
                sender.sendMessage("§aИгроку " + currentPlayerName + " добавлен 1 бонусный варп! Теперь: §e" + newBonus);

                Player target = Bukkit.getPlayer(playerId);
                if (target != null) {
                    target.sendMessage("§eВам был выдан 1 бонусный варп! Всего: §6" + newBonus);
                }
            }
        });
    }

    private void handleSet(CommandSender sender, String[] args) {
        String playerName = args[1];
        String amountStr = args[2];

        Bukkit.getScheduler().runTaskAsynchronously(WarpBonusPlugin.getInstance(), () -> {
            UUID playerId = getUUIDFromName(playerName);
            if (playerId == null) {
                sender.sendMessage("§cИгрок " + playerName + " не найден!");
                return;
            }

            try {
                int amount = Integer.parseInt(amountStr);
                if (amount < 0) {
                    sender.sendMessage("§cКоличество не может быть отрицательным!");
                    return;
                }
                bonusManager.setBonusWarps(playerId, playerName, amount);
                String currentPlayerName = bonusManager.getPlayerName(playerId);
                sender.sendMessage("§aУстановлено §e" + amount + "§a бонусных варпов для игрока " + currentPlayerName);

                Player target = Bukkit.getPlayer(playerId);
                if (target != null) {
                    target.sendMessage("§eВаше количество бонусных варпов установлено на: §6" + amount);
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cНекорректное число!");
            }
        });
    }

    private void handleCheck(CommandSender sender, String[] args) {
        String playerName = args[1];

        Bukkit.getScheduler().runTaskAsynchronously(WarpBonusPlugin.getInstance(), () -> {
            UUID playerId = getUUIDFromName(playerName);
            if (playerId == null) {
                sender.sendMessage("§cИгрок " + playerName + " не найден!");
                return;
            }

            int bonus = bonusManager.getBonusWarps(playerId);
            String currentPlayerName = bonusManager.getPlayerName(playerId);
            sender.sendMessage("§eИгрок " + currentPlayerName + " имеет §6" + bonus + "§e бонусных варпов");
        });
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage("§6=== Список бонусных варпов ===");
        Map<String, Integer> warps = bonusManager.getReadableBonusWarps();
        if (warps.isEmpty()) {
            sender.sendMessage("§7Нет записей о бонусных варпах");
        } else {
            warps.forEach((playerName, amount) -> {
                sender.sendMessage("§e" + playerName + "§7: §6" + amount + "§7 бонусных варпов");
            });
        }
    }

    private UUID getUUIDFromName(String playerName) {
        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getUniqueId();
        }

        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Подсказки для подкоманд
            completions.addAll(Arrays.asList("add", "set", "check", "list"));
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            // Подсказки для имен игроков
            String partialName = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            // Подсказки для количества при добавлении
            completions.add("1");
            completions.add("5");
            completions.add("10");
        }

        return completions;
    }
}
