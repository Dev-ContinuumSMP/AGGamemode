package dev.ag.gamemode.listener;

import dev.ag.gamemode.AGGamemodePlugin;
import dev.ag.gamemode.model.GamemodeType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final AGGamemodePlugin plugin;

    public ChatListener(AGGamemodePlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        GamemodeType mode = plugin.getPlayerModeService().getMode(event.getPlayer().getUniqueId());
        if (mode == GamemodeType.TRAVELER) {
            return;
        }

        String symbol = plugin.getSymbol(mode);
        if (symbol.isBlank()) {
            return;
        }

        String currentFormat = event.getFormat();
        event.setFormat(symbol + " " + currentFormat);
    }
}
