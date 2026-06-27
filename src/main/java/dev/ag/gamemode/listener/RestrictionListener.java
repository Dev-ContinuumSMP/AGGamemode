package dev.ag.gamemode.listener;

import dev.ag.gamemode.AGGamemodePlugin;
import dev.ag.gamemode.model.GamemodeType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.UUID;

public class RestrictionListener implements Listener {

    private final AGGamemodePlugin plugin;

    public RestrictionListener(AGGamemodePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        String rewritten = plugin.rewriteTradeCommandIfNeeded(player, event.getMessage());
        if (!rewritten.equals(event.getMessage())) {
            event.setMessage(rewritten);
        }

        if (!plugin.isRestrictedMode(player)) {
            return;
        }

        if (plugin.isTradeManagementCommand(event.getMessage())) {
            return;
        }

        if (plugin.canTradeWithinRestrictedGroup(player, event.getMessage())) {
            return;
        }

        if (!plugin.isBlockedCommand(event.getMessage())) {
            return;
        }

        event.setCancelled(true);
        GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        if (plugin.isTradeCommand(event.getMessage())) {
            plugin.send(player, "cannot-trade-outside-group", "%mode%", mode.prettyName());
            return;
        }
        plugin.send(player, "cannot-use-command", "%mode%", mode.prettyName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("restrictions.prevent-item-drop", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.isRestrictedMode(player)) {
            return;
        }

        if (plugin.isInActiveTrade(player)) {
            return;
        }

        event.setCancelled(true);
        GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        plugin.send(player, "cannot-drop-items", "%mode%", mode.prettyName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("restrictions.prevent-pickup-from-players", true)) {
            return;
        }

        UUID thrower = event.getItem().getThrower();
        if (thrower == null || thrower.equals(player.getUniqueId())) {
            return;
        }

        Player source = Bukkit.getPlayer(thrower);
        if (source != null) {
            if (plugin.hasActiveTradeBetween(player, source)) {
                return;
            }

            if (!plugin.isRestrictedMode(player)) {
                event.setCancelled(true);
                return;
            }

            GamemodeType receiverMode = plugin.getPlayerModeService().getMode(player.getUniqueId());
            GamemodeType sourceMode = plugin.getPlayerModeService().getMode(source.getUniqueId());
            boolean sameRestrictedMode = (receiverMode == GamemodeType.IRONMAN || receiverMode == GamemodeType.HARDCORE)
                    && receiverMode == sourceMode;
            boolean sameTradeGroup = plugin.getPlayerModeService().sharesTradeGroup(player.getUniqueId(), source.getUniqueId());
            if (sameRestrictedMode && sameTradeGroup) {
                return;
            }
        }

        event.setCancelled(true);
        GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        plugin.send(player, "cannot-pickup-player-drop", "%mode%", mode.prettyName());
    }
}
