package dev.ag.gamemode.listener;

import dev.ag.gamemode.AGGamemodePlugin;
import dev.ag.gamemode.model.GamemodeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HardcoreListener implements Listener {

    private static final String CHOICE_TITLE = "\u00a78Hardcore Death Choice";

    private final AGGamemodePlugin plugin;
    private final Set<UUID> pendingChoice = new HashSet<>();
    private final Set<UUID> openingGuard = new HashSet<>();

    public HardcoreListener(AGGamemodePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        if (mode != GamemodeType.HARDCORE) {
            return;
        }

        Player killer = player.getKiller();
        String killerName = killer == null ? "Unknown" : killer.getName();
        plugin.broadcast("hardcore-death-announcement", "%player%", player.getName(), "%killer%", killerName);

        pendingChoice.add(player.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pendingChoice.contains(player.getUniqueId())) {
            return;
        }

        player.getScheduler().runDelayed(plugin, task -> openChoiceMenu(player), null, 10L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!pendingChoice.contains(player.getUniqueId())) {
            return;
        }

        player.getScheduler().runDelayed(plugin, task -> openChoiceMenu(player), null, 20L);
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!CHOICE_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        UUID uuid = player.getUniqueId();
        if (!pendingChoice.contains(uuid)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 11) {
            pendingChoice.remove(uuid);
            openingGuard.remove(uuid);
            plugin.demoteHardcore(player);
            player.closeInventory();
            return;
        }

        if (slot == 15) {
            pendingChoice.remove(uuid);
            openingGuard.remove(uuid);
            int banDays = plugin.getConfig().getInt("hardcore.temp-ban-days", 3);
            plugin.banHardcoreForDays(player, banDays);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!CHOICE_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!pendingChoice.contains(uuid)) {
            openingGuard.remove(uuid);
            return;
        }

        if (openingGuard.contains(uuid)) {
            return;
        }

        openingGuard.add(uuid);
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) {
                openingGuard.remove(uuid);
                return;
            }

            if (pendingChoice.contains(uuid)) {
                openChoiceMenu(player);
            }
            openingGuard.remove(uuid);
        }, null, 1L);
    }

    @SuppressWarnings("deprecation")
    private void openChoiceMenu(Player player) {
        if (!player.isOnline() || !pendingChoice.contains(player.getUniqueId())) {
            return;
        }

        int banDays = plugin.getConfig().getInt("hardcore.temp-ban-days", 3);
        Inventory inventory = Bukkit.createInventory(null, 27, CHOICE_TITLE);
        inventory.setItem(11, createChoiceItem(
                Material.LIGHT_BLUE_WOOL,
                "&bDowngrade to Ironman",
                List.of("&7Keep playing with restrictions.", "&7You will be demoted immediately.")
        ));
        inventory.setItem(15, createChoiceItem(
                Material.GRAY_CONCRETE,
                "&7Take " + banDays + " Day Ban",
                List.of("&7Accept a temporary hardcore ban.", "&7Ban length: &f" + banDays + " days")
        ));

        player.openInventory(inventory);
    }

    @SuppressWarnings("deprecation")
    private ItemStack createChoiceItem(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName(plugin.color(name));
        meta.setLore(lore.stream().map(plugin::color).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }
}
