package dev.ag.gamemode;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private record ActiveTrade(UUID partner, long expiresAtMillis) {
    }

    private final AGGamemodePlugin plugin;
    private final Map<UUID, UUID> pendingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingTargetByRequester = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveTrade> activeTrades = new ConcurrentHashMap<>();

    public TradeCommand(AGGamemodePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&6AGGamemode &8| &cThis command can only be used by players."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.color("&7Usage: /trade <player>, /trade request <player>, /trade accept [player], /trade decline [player], /trade cancel"));
            return true;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("request")) {
            if (args.length < 2) {
                player.sendMessage(plugin.color("&cUsage: /trade request <player>"));
                return true;
            }
            return sendTradeRequest(player, args[1]);
        }

        if (root.equals("accept")) {
            String requesterName = args.length >= 2 ? args[1] : null;
            return acceptTradeRequest(player, requesterName);
        }

        if (root.equals("deny") || root.equals("decline")) {
            String requesterName = args.length >= 2 ? args[1] : null;
            return declineTradeRequest(player, requesterName);
        }

        if (root.equals("cancel")) {
            return cancelTradeRequest(player);
        }

        return sendTradeRequest(player, args[0]);
    }

    private boolean sendTradeRequest(Player requester, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }

        if (target == null) {
            requester.sendMessage(plugin.color("&cPlayer not found."));
            return true;
        }

        if (target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(plugin.color("&cYou cannot trade with yourself."));
            return true;
        }

        if (!plugin.canPlayersTrade(requester, target)) {
            if (plugin.isRestrictedMode(requester)) {
                plugin.send(requester, "cannot-trade-outside-group", "%mode%", plugin.getPlayerModeService().getMode(requester.getUniqueId()).prettyName());
            } else {
                requester.sendMessage(plugin.color("&cYou can only use this trade flow inside restricted mode groups."));
            }
            return true;
        }

        pendingByTarget.put(target.getUniqueId(), requester.getUniqueId());
        pendingTargetByRequester.put(requester.getUniqueId(), target.getUniqueId());

        requester.sendMessage(plugin.color("&aTrade request sent to &e" + target.getName() + "&a."));
        target.sendMessage(plugin.color("&e" + requester.getName() + " &7wants to trade with you."));
        target.sendMessage(plugin.color("&7Use &e/trade accept " + requester.getName() + " &7or &e/trade decline " + requester.getName()));
        return true;
    }

    private boolean acceptTradeRequest(Player target, String requesterName) {
        UUID requesterUuid = pendingByTarget.get(target.getUniqueId());
        if (requesterUuid == null) {
            target.sendMessage(plugin.color("&cYou have no pending trade requests."));
            return true;
        }

        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requester == null || !requester.isOnline()) {
            clearRequest(target.getUniqueId(), requesterUuid);
            target.sendMessage(plugin.color("&cThat requester is no longer online."));
            return true;
        }

        if (requesterName != null && !requesterName.isBlank() && !requester.getName().equalsIgnoreCase(requesterName)) {
            target.sendMessage(plugin.color("&cPending request is from &e" + requester.getName() + "&c."));
            return true;
        }

        if (!plugin.canPlayersTrade(requester, target)) {
            clearRequest(target.getUniqueId(), requesterUuid);
            target.sendMessage(plugin.color("&cTrade request is no longer valid (group or mode mismatch)."));
            requester.sendMessage(plugin.color("&cYour trade request is no longer valid."));
            return true;
        }

        clearRequest(target.getUniqueId(), requesterUuid);
        startActiveTrade(requester, target);
        target.sendMessage(plugin.color("&aYou accepted the trade request from &e" + requester.getName() + "&a."));
        requester.sendMessage(plugin.color("&a" + target.getName() + " accepted your trade request."));
        int windowSeconds = Math.max(15, plugin.getConfig().getInt("trade.active-session-seconds", 120));
        requester.sendMessage(plugin.color("&7Trade session active for &e" + windowSeconds + "s&7. Drop items to each other now."));
        target.sendMessage(plugin.color("&7Trade session active for &e" + windowSeconds + "s&7. Drop items to each other now."));
        return true;
    }

    private boolean declineTradeRequest(Player target, String requesterName) {
        UUID requesterUuid = pendingByTarget.get(target.getUniqueId());
        if (requesterUuid == null) {
            target.sendMessage(plugin.color("&cYou have no pending trade requests."));
            return true;
        }

        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requesterName != null && requester != null && !requesterName.isBlank() && !requester.getName().equalsIgnoreCase(requesterName)) {
            target.sendMessage(plugin.color("&cPending request is from &e" + requester.getName() + "&c."));
            return true;
        }

        clearRequest(target.getUniqueId(), requesterUuid);
        target.sendMessage(plugin.color("&7Trade request declined."));
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(plugin.color("&c" + target.getName() + " declined your trade request."));
        }
        return true;
    }

    private boolean cancelTradeRequest(Player requester) {
        UUID targetUuid = pendingTargetByRequester.get(requester.getUniqueId());
        if (targetUuid == null) {
            requester.sendMessage(plugin.color("&cYou have no outgoing trade request to cancel."));
            return true;
        }

        clearRequest(targetUuid, requester.getUniqueId());
        requester.sendMessage(plugin.color("&7Trade request cancelled."));
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(plugin.color("&7" + requester.getName() + " cancelled their trade request."));
        }
        return true;
    }

    private void clearRequest(UUID targetUuid, UUID requesterUuid) {
        pendingByTarget.remove(targetUuid);
        pendingTargetByRequester.remove(requesterUuid);
    }

    public boolean isInActiveTrade(Player player) {
        if (player == null) {
            return false;
        }

        ActiveTrade state = activeTrades.get(player.getUniqueId());
        if (state == null) {
            return false;
        }

        if (System.currentTimeMillis() > state.expiresAtMillis) {
            activeTrades.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    public boolean hasActiveTradeBetween(Player first, Player second) {
        if (first == null || second == null) {
            return false;
        }

        ActiveTrade firstState = activeTrades.get(first.getUniqueId());
        ActiveTrade secondState = activeTrades.get(second.getUniqueId());
        if (firstState == null || secondState == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now > firstState.expiresAtMillis) {
            activeTrades.remove(first.getUniqueId());
            return false;
        }

        if (now > secondState.expiresAtMillis) {
            activeTrades.remove(second.getUniqueId());
            return false;
        }

        return firstState.partner.equals(second.getUniqueId()) && secondState.partner.equals(first.getUniqueId());
    }

    private void startActiveTrade(Player first, Player second) {
        int windowSeconds = Math.max(15, plugin.getConfig().getInt("trade.active-session-seconds", 120));
        long expires = System.currentTimeMillis() + (windowSeconds * 1000L);
        activeTrades.put(first.getUniqueId(), new ActiveTrade(second.getUniqueId(), expires));
        activeTrades.put(second.getUniqueId(), new ActiveTrade(first.getUniqueId(), expires));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("request", "accept", "decline", "cancel"), args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("request") || args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline") || args[0].equalsIgnoreCase("deny"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
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
