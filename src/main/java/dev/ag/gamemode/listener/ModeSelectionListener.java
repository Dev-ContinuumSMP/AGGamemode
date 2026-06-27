package dev.ag.gamemode.listener;

import dev.ag.gamemode.AGGamemodePlugin;
import dev.ag.gamemode.model.GamemodeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ModeSelectionListener implements Listener {

    private static final String MENU_PATH = "menu";
    private static final String MODES_PATH = "modes";
    private static final String NBT_PATH = "agauctions-nbt";

    private final AGGamemodePlugin plugin;
    private final Set<UUID> openingGuard = new HashSet<>();

    public ModeSelectionListener(AGGamemodePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerModeService().hasSelectedMode(player.getUniqueId())) {
            return;
        }

        player.getScheduler().runDelayed(plugin, task -> openMenu(player), null, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerModeService().hasSelectedMode(player.getUniqueId())) {
            return;
        }

        player.getScheduler().runDelayed(plugin, task -> openMenu(player), null, 10L);
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!isMenuTitle(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        GamemodeType selected = modeForSlot(slot);
        if (selected == null) {
            return;
        }

        plugin.applyMode(player, selected);
        plugin.send(player, "set-self", "%mode%", selected.prettyName());
        openingGuard.remove(player.getUniqueId());
        player.closeInventory();
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!isMenuTitle(event.getView().getTitle())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (plugin.getPlayerModeService().hasSelectedMode(uuid)) {
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

            if (!plugin.getPlayerModeService().hasSelectedMode(uuid)) {
                openMenu(player);
            }
            openingGuard.remove(uuid);
        }, null, 1L);
    }

    @SuppressWarnings("deprecation")
    private void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, getMenuSize(), getMenuTitle());
        applyFiller(inventory);

        for (GamemodeType mode : GamemodeType.values()) {
            int slot = getModeSlot(mode);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, createModeItem(mode));
        }

        player.openInventory(inventory);
    }

    private String getMenuTitle() {
        String raw = plugin.getGuiConfig().getString(MENU_PATH + ".title", "&8Choose Your Gamemode");
        return plugin.color(raw);
    }

    private boolean isMenuTitle(String title) {
        return title.equals(getMenuTitle());
    }

    private int getMenuSize() {
        int configured = plugin.getGuiConfig().getInt(MENU_PATH + ".size", 27);
        if (configured < 9 || configured > 54 || configured % 9 != 0) {
            return 27;
        }
        return configured;
    }

    private GamemodeType modeForSlot(int slot) {
        for (GamemodeType mode : GamemodeType.values()) {
            if (getModeSlot(mode) == slot) {
                return mode;
            }
        }
        return null;
    }

    private int getModeSlot(GamemodeType mode) {
        String path = modePath(mode) + ".slot";
        int fallback = switch (mode) {
            case TRAVELER -> 11;
            case IRONMAN -> 13;
            case HARDCORE -> 15;
        };
        return plugin.getGuiConfig().getInt(path, fallback);
    }

    @SuppressWarnings("deprecation")
    private ItemStack createModeItem(GamemodeType mode) {
        ConfigurationSection section = plugin.getGuiConfig().getConfigurationSection(modePath(mode));
        Material fallbackMaterial = switch (mode) {
            case TRAVELER -> Material.COMPASS;
            case IRONMAN -> Material.IRON_INGOT;
            case HARDCORE -> Material.NETHERITE_SWORD;
        };

        String configuredMaterial = section == null ? null : section.getString("material");
        Material material = configuredMaterial == null ? fallbackMaterial : Material.matchMaterial(configuredMaterial);
        if (material == null) {
            material = fallbackMaterial;
        }

        String defaultName = switch (mode) {
            case TRAVELER -> "&9Traveler";
            case IRONMAN -> "&7Ironman";
            case HARDCORE -> "&3Hardcore";
        };

        List<String> defaultLore = switch (mode) {
            case TRAVELER -> List.of("&7Normal experience.", "&7Trading and shops are allowed.");
            case IRONMAN -> List.of("&7No player trading.", "&7No shop commands.", "&7Chat symbol: %symbol%");
            case HARDCORE -> List.of("&7Same restrictions as Ironman.", "&7Permanent consequence on death.", "&7Chat symbol: %symbol%");
        };

        String rawName = section == null ? defaultName : section.getString("name", defaultName);
        List<String> rawLore = section == null ? defaultLore : section.getStringList("lore");
        if (rawLore.isEmpty()) {
            rawLore = defaultLore;
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String symbol = plugin.getSymbol(mode);
        meta.setDisplayName(plugin.color(rawName.replace("%symbol%", symbol)));
        meta.setLore(rawLore.stream().map(line -> line.replace("%symbol%", symbol)).map(plugin::color).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (section != null) {
            applyAgaNbt(meta.getPersistentDataContainer(), section.getConfigurationSection(NBT_PATH));
        }

        stack.setItemMeta(meta);
        return stack;
    }

    @SuppressWarnings("deprecation")
    private void applyFiller(Inventory inventory) {
        ConfigurationSection section = plugin.getGuiConfig().getConfigurationSection(MENU_PATH + ".filler");
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        String materialRaw = section.getString("material", "GRAY_STAINED_GLASS_PANE");
        Material material = Material.matchMaterial(materialRaw);
        if (material == null) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }

        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(section.getString("name", " ")));
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(plugin::color).toList());
            }
            filler.setItemMeta(meta);
        }

        for (int slot : section.getIntegerList("slots")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private void applyAgaNbt(PersistentDataContainer container, ConfigurationSection nbtSection) {
        if (nbtSection == null) {
            return;
        }

        for (String key : nbtSection.getKeys(false)) {
            String value = nbtSection.getString(key);
            if (value == null) {
                continue;
            }

            NamespacedKey namespacedKey = key.contains(":")
                    ? NamespacedKey.fromString(key)
                    : NamespacedKey.fromString("agauctions:" + key.toLowerCase(Locale.ROOT));
            if (namespacedKey != null) {
                container.set(namespacedKey, PersistentDataType.STRING, value);
            }
        }
    }

    private String modePath(GamemodeType mode) {
        return MODES_PATH + "." + mode.name().toLowerCase(Locale.ROOT);
    }
}
