package me.nolover.alwayssit.command;

import me.nolover.alwayssit.AlwaysSit;
import me.nolover.alwayssit.manager.FlyingEntityRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command /asit <player> [entity]
 * Toggles or sets automatic sitting on the furthest launched projectile.
 */
public class AlwaysSitCommand implements CommandExecutor, TabCompleter {

    private final AlwaysSit plugin;

    public AlwaysSitCommand(AlwaysSit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alwayssit.use")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }

        if (target == null) {
            sender.sendMessage(plugin.getMessage("player-not-found", "%player%", targetName, "{player}", targetName));
            return true;
        }

        // 1 argument: /asit <player> -> remove from list
        if (args.length == 1) {
            boolean removed = plugin.getSittingManager().stopTracking(target);
            if (removed) {
                sender.sendMessage(plugin.getMessage("disabled", "%player%", target.getName(), "{player}", target.getName()));
                if (!sender.equals(target)) {
                    target.sendMessage(plugin.getMessage("target-notified-disabled"));
                }
            } else {
                sender.sendMessage(plugin.getMessage("not-in-list", "%player%", target.getName(), "{player}", target.getName()));
                sendUsage(sender);
            }
            return true;
        }

        // 2 arguments: /asit <player> <entity> -> enable / update sitting on entity
        String entityArg = args[1];
        boolean wildcard = FlyingEntityRegistry.isWildcard(entityArg);
        EntityType entityType = FlyingEntityRegistry.parse(entityArg);

        if (!wildcard && entityType == null) {
            sender.sendMessage(plugin.getMessage("invalid-entity", "%entity%", entityArg, "{entity}", entityArg));
            return true;
        }

        boolean wasTracked = plugin.getSittingManager().isTracking(target.getUniqueId());
        plugin.getSittingManager().startTracking(target, wildcard ? null : entityType);

        String entityName = FlyingEntityRegistry.getDisplayName(entityType);

        if (wasTracked) {
            sender.sendMessage(plugin.getMessage("updated",
                    "%player%", target.getName(), "{player}", target.getName(),
                    "%entity%", entityName, "{entity}", entityName));
        } else {
            sender.sendMessage(plugin.getMessage("enabled",
                    "%player%", target.getName(), "{player}", target.getName(),
                    "%entity%", entityName, "{entity}", entityName));
        }

        if (!sender.equals(target)) {
            target.sendMessage(plugin.getMessage("target-notified-enabled",
                    "%entity%", entityName, "{entity}", entityName));
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        List<String> lines = plugin.getConfig().getStringList("messages.usage");
        if (lines.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "==== AlwaysSit Помощь ====");
            sender.sendMessage(ChatColor.YELLOW + "/asit <ник> <сущность> " + ChatColor.GRAY + "- включить посадку на самую дальнюю запущенную сущность");
            sender.sendMessage(ChatColor.YELLOW + "/asit <ник> " + ChatColor.GRAY + "- удалить игрока из списка AlwaysSit");
        } else {
            for (String line : lines) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("alwayssit.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }

        if (args.length == 2) {
            // Suggest all flying / projectile entities
            return StringUtil.copyPartialMatches(args[1], FlyingEntityRegistry.getSuggestions(), new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
