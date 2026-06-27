package dev.ag.gamemode;

import dev.ag.gamemode.model.GamemodeType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class GamemodeCommand implements CommandExecutor, TabCompleter {

    private final AGGamemodePlugin plugin;

    public GamemodeCommand(AGGamemodePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&6AGGamemode &8| &cThis command can only be used by players."));
            return true;
        }

        if (args.length == 0) {
            GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
            plugin.send(player, "current-mode", "%mode%", mode.prettyName());
            player.sendMessage(plugin.color("&7Use &e/agg help &7for commands."));
            return true;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("help")) {
            sendHelp(player);
            return true;
        }

        if (root.equals("group")) {
            handleGroupCommand(player, args);
            return true;
        }

        if (root.equals("set")) {
            handleAdminSet(player, args);
            return true;
        }

        if (!player.hasPermission("aggamemode.self")) {
            player.sendMessage(plugin.color("&6AGGamemode &8| &cYou are missing permission for that action."));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(plugin.color("&cUsage: /agg [mode], /agg set <player> <mode>, /agg group <create|rename|join|leave|info>, /agg help"));
            return true;
        }

        GamemodeType mode = GamemodeType.fromString(args[0]);
        if (mode == null) {
            player.sendMessage(plugin.color("&cInvalid mode. Use traveler, ironman, or hardcore."));
            return true;
        }

        plugin.applyMode(player, mode);
        plugin.send(player, "set-self", "%mode%", mode.prettyName());
        return true;
    }

    private void handleAdminSet(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage(plugin.color("&cUsage: /agg set <player> <mode>"));
            return;
        }

        if (!player.hasPermission("aggamemode.admin")) {
            player.sendMessage(plugin.color("&6AGGamemode &8| &cYou are missing permission for that action."));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.color("&cPlayer not found."));
            return;
        }

        GamemodeType mode = GamemodeType.fromString(args[2]);
        if (mode == null) {
            player.sendMessage(plugin.color("&cInvalid mode. Use traveler, ironman, or hardcore."));
            return;
        }

        plugin.applyMode(target, mode);
        plugin.send(player, "set-other", "%player%", target.getName(), "%mode%", mode.prettyName());
        plugin.send(target, "set-self", "%mode%", mode.prettyName());
    }

    private void handleGroupCommand(Player player, String[] args) {
        if (!plugin.isRestrictedMode(player)) {
            player.sendMessage(plugin.color("&cTrade groups are only used in Ironman and Hardcore."));
            return;
        }

        GamemodeType selfMode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        plugin.getPlayerModeService().ensureTradeGroup(player.getUniqueId(), selfMode);

        if (args.length == 1) {
            plugin.getGroupManagementListener().openMainMenu(player);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("gui") || action.equals("manage")) {
            plugin.getGroupManagementListener().openMainMenu(player);
            return;
        }

        if (action.equals("create") || action.equals("rename")) {
            if (args.length != 3) {
                player.sendMessage(plugin.color("&cUsage: /agg group " + action + " <name>"));
                return;
            }

            String normalized = normalizeGroupName(args[2]);
            if (normalized == null) {
                player.sendMessage(plugin.color("&cInvalid name. Use 3-24 chars: letters, numbers, underscore, dash."));
                return;
            }

            plugin.getPlayerModeService().setTradeGroup(player.getUniqueId(), normalized);
            if (action.equals("create")) {
                player.sendMessage(plugin.color("&aCreated trade group: &b" + normalized));
            } else {
                player.sendMessage(plugin.color("&aRenamed your trade group to: &b" + normalized));
            }
            return;
        }

        if (action.equals("join")) {
            if (args.length != 3) {
                player.sendMessage(plugin.color("&cUsage: /agg group join <player>"));
                return;
            }

            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                player.sendMessage(plugin.color("&cPlayer not found."));
                return;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(plugin.color("&cYou are already in your own group."));
                return;
            }

            if (!plugin.isRestrictedMode(target)) {
                player.sendMessage(plugin.color("&cThat player is not in Ironman/Hardcore."));
                return;
            }

            GamemodeType targetMode = plugin.getPlayerModeService().getMode(target.getUniqueId());
            if (selfMode != targetMode) {
                player.sendMessage(plugin.color("&cYou can only join groups of players in your same mode."));
                return;
            }

            plugin.getGroupManagementListener().requestJoin(player, target);
            return;
        }

        if (action.equals("accept")) {
            if (args.length < 3) {
                player.sendMessage(plugin.color("&cUsage: /agg group accept <player>"));
                return;
            }

            plugin.getGroupManagementListener().acceptJoinRequest(player, args[2]);
            return;
        }

        if (action.equals("decline")) {
            if (args.length < 3) {
                player.sendMessage(plugin.color("&cUsage: /agg group decline <player>"));
                return;
            }

            plugin.getGroupManagementListener().declineJoinRequest(player, args[2]);
            return;
        }

        if (action.equals("leave")) {
            if (args.length != 2) {
                player.sendMessage(plugin.color("&cUsage: /agg group leave"));
                return;
            }

            plugin.getPlayerModeService().assignPersonalTradeGroup(player.getUniqueId(), selfMode);
            String group = plugin.getPlayerModeService().getTradeGroup(player.getUniqueId());
            player.sendMessage(plugin.color("&aYou left the shared group. New private group: &b" + group));
            return;
        }

        if (action.equals("info")) {
            if (args.length == 2) {
                showGroupInfo(player, player);
                return;
            }

            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                player.sendMessage(plugin.color("&cPlayer not found."));
                return;
            }

            showGroupInfo(player, target);
            return;
        }

        player.sendMessage(plugin.color("&cUsage: /agg group <create|rename|join|accept|decline|leave|info>"));
    }

    private void showGroupInfo(Player viewer, Player subject) {
        GamemodeType mode = plugin.getPlayerModeService().getMode(subject.getUniqueId());
        if (mode != GamemodeType.IRONMAN && mode != GamemodeType.HARDCORE) {
            viewer.sendMessage(plugin.color("&cThat player is not in a restricted mode."));
            return;
        }

        plugin.getPlayerModeService().ensureTradeGroup(subject.getUniqueId(), mode);
        String group = plugin.getPlayerModeService().getTradeGroup(subject.getUniqueId());
        List<String> members = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getPlayerModeService().getMode(p.getUniqueId()) == mode)
                .filter(p -> group.equalsIgnoreCase(plugin.getPlayerModeService().getTradeGroup(p.getUniqueId())))
                .map(Player::getName)
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());

        viewer.sendMessage(plugin.color("&8&m--------------------------------"));
        viewer.sendMessage(plugin.color("&7Group: &b" + group));
        viewer.sendMessage(plugin.color("&7Mode: &f" + mode.prettyName()));
        viewer.sendMessage(plugin.color("&7Online members: &f" + String.join("&7, &f", members)));
        viewer.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    private String normalizeGroupName(String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.length() < 3 || trimmed.length() > 24) {
            return null;
        }

        if (!trimmed.matches("[a-z0-9_-]+")) {
            return null;
        }

        return trimmed;
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&6AGGamemode &7Commands"));
        player.sendMessage(plugin.color("&e/agg <traveler|ironman|hardcore> &7- Set your mode"));
        player.sendMessage(plugin.color("&e/agg set <player> <mode> &7- Admin set mode"));
        player.sendMessage(plugin.color("&e/agg group &7- Open trade group manager GUI"));
        player.sendMessage(plugin.color("&e/agg group gui &7- Open trade group manager GUI"));
        player.sendMessage(plugin.color("&e/agg group create <name> &7- Create/switch to named group"));
        player.sendMessage(plugin.color("&e/agg group rename <name> &7- Rename your current group"));
        player.sendMessage(plugin.color("&e/agg group join <player> &7- Request to join that player's group"));
        player.sendMessage(plugin.color("&e/agg group accept <player> &7- Accept a join request"));
        player.sendMessage(plugin.color("&e/agg group decline <player> &7- Decline a join request"));
        player.sendMessage(plugin.color("&e/agg group leave &7- Leave to private group"));
        player.sendMessage(plugin.color("&e/agg group info [player] &7- View group info"));
        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> root = new ArrayList<>(plugin.getModeSuggestions());
            if (sender.hasPermission("aggamemode.admin")) {
                root.add("set");
            }
            root.add("group");
            root.add("help");
            return filter(root, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
            return filter(List.of("gui", "manage", "create", "rename", "join", "accept", "decline", "leave", "info"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("group") && args[1].equalsIgnoreCase("join")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("group") && (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("decline"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("group") && args[1].equalsIgnoreCase("info")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).collect(Collectors.toList()), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(plugin.getModeSuggestions(), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> input, String startsWith) {
        String lowered = startsWith.toLowerCase(Locale.ROOT);
        return input.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
                .collect(Collectors.toList());
    }
}
