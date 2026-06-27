package dev.ag.gamemode.listener;

import dev.ag.gamemode.AGGamemodePlugin;
import dev.ag.gamemode.model.GamemodeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GroupManagementListener implements Listener {

    private static final String MAIN_TITLE = "&8[&9AG&8] &7Trade Groups";
    private static final String JOIN_TITLE = "&8[&9AG&8] &7Join A Group";

    private final AGGamemodePlugin plugin;
    private final NamespacedKey joinTargetKey;
    private final Map<UUID, PromptType> pendingPrompt = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingJoinRequestsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingJoinOwnerByRequester = new ConcurrentHashMap<>();

    private enum PromptType {
        CREATE,
        RENAME
    }

    public GroupManagementListener(AGGamemodePlugin plugin) {
        this.plugin = plugin;
        this.joinTargetKey = new NamespacedKey(plugin, "group_join_target");
    }

    @SuppressWarnings("deprecation")
    public void openMainMenu(Player player) {
        if (!plugin.isRestrictedMode(player)) {
            player.sendMessage(plugin.color("&cTrade groups are only used in Ironman and Hardcore."));
            return;
        }

        GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        plugin.getPlayerModeService().ensureTradeGroup(player.getUniqueId(), mode);
        String group = plugin.getPlayerModeService().getTradeGroup(player.getUniqueId());

        Inventory inventory = Bukkit.createInventory(null, 27, plugin.color(MAIN_TITLE));
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, createItem(Material.PAPER, "&bYour Group", List.of(
                "&7Mode: &f" + mode.prettyName(),
                "&7Group: &f" + group,
                "&7Click &fInfo &7to see members"
        )));
        inventory.setItem(10, createItem(Material.LIGHT_BLUE_WOOL, "&bCreate Group", List.of("&7Create/switch to a named group")));
        inventory.setItem(12, createItem(Material.NAME_TAG, "&9Rename Group", List.of("&7Rename your current group")));
        inventory.setItem(14, createItem(Material.PLAYER_HEAD, "&7Join Player Group", List.of("&7Browse online players in your mode")));
        inventory.setItem(16, createItem(Material.BARRIER, "&8Leave Shared Group", List.of("&7Return to a private group")));
        inventory.setItem(22, createItem(Material.BOOK, "&fInfo", List.of("&7Show your current group members")));

        player.openInventory(inventory);
    }

    @SuppressWarnings("deprecation")
    private void openJoinMenu(Player player) {
        GamemodeType selfMode = plugin.getPlayerModeService().getMode(player.getUniqueId());

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .filter(other -> plugin.getPlayerModeService().getMode(other.getUniqueId()) == selfMode)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        int size = Math.max(9, ((candidates.size() + 1 + 8) / 9) * 9);
        size = Math.min(size, 54);

        Inventory inventory = Bukkit.createInventory(null, size, plugin.color(JOIN_TITLE));

        for (Player candidate : candidates) {
            plugin.getPlayerModeService().ensureTradeGroup(candidate.getUniqueId(), selfMode);
            String group = plugin.getPlayerModeService().getTradeGroup(candidate.getUniqueId());

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) {
                continue;
            }

            meta.setOwningPlayer(candidate);
            meta.setDisplayName(plugin.color("&b" + candidate.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.color("&7Mode: &f" + selfMode.prettyName()));
            lore.add(plugin.color("&7Group: &f" + group));
            lore.add(plugin.color("&7Click to request joining this group"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(joinTargetKey, PersistentDataType.STRING, candidate.getUniqueId().toString());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            head.setItemMeta(meta);

            inventory.addItem(head);
        }

        inventory.setItem(size - 1, createItem(Material.ARROW, "&7Back", List.of("&7Return to group menu")));
        player.openInventory(inventory);
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        String mainTitle = plugin.color(MAIN_TITLE);
        String joinTitle = plugin.color(JOIN_TITLE);
        if (!title.equals(mainTitle) && !title.equals(joinTitle)) {
            return;
        }

        event.setCancelled(true);

        if (title.equals(mainTitle)) {
            handleMainMenuClick(player, event.getRawSlot());
            return;
        }

        handleJoinMenuClick(player, event.getCurrentItem(), event.getRawSlot(), event.getInventory().getSize());
    }

    private void handleMainMenuClick(Player player, int slot) {
        GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        if (mode != GamemodeType.IRONMAN && mode != GamemodeType.HARDCORE) {
            player.closeInventory();
            player.sendMessage(plugin.color("&cTrade groups are only used in Ironman and Hardcore."));
            return;
        }

        if (slot == 10) {
            startPrompt(player, PromptType.CREATE, "&7Type your new group name in chat. Type &ccancel &7to abort.");
            return;
        }

        if (slot == 12) {
            startPrompt(player, PromptType.RENAME, "&7Type the new group name in chat. Type &ccancel &7to abort.");
            return;
        }

        if (slot == 14) {
            openJoinMenu(player);
            return;
        }

        if (slot == 16) {
            plugin.getPlayerModeService().assignPersonalTradeGroup(player.getUniqueId(), mode);
            String group = plugin.getPlayerModeService().getTradeGroup(player.getUniqueId());
            player.sendMessage(plugin.color("&aYou left the shared group. Private group: &b" + group));
            openMainMenu(player);
            return;
        }

        if (slot == 22) {
            sendGroupInfo(player, player);
            return;
        }
    }

    private void handleJoinMenuClick(Player player, ItemStack clicked, int slot, int size) {
        if (slot == size - 1) {
            openMainMenu(player);
            return;
        }

        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        String rawTarget = meta.getPersistentDataContainer().get(joinTargetKey, PersistentDataType.STRING);
        if (rawTarget == null || rawTarget.isBlank()) {
            return;
        }

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(rawTarget);
        } catch (IllegalArgumentException ignored) {
            player.sendMessage(plugin.color("&cThat group target is invalid."));
            return;
        }

        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            player.sendMessage(plugin.color("&cThat player is no longer online."));
            openJoinMenu(player);
            return;
        }

        GamemodeType selfMode = plugin.getPlayerModeService().getMode(player.getUniqueId());
        GamemodeType targetMode = plugin.getPlayerModeService().getMode(target.getUniqueId());
        if (selfMode != targetMode || (selfMode != GamemodeType.IRONMAN && selfMode != GamemodeType.HARDCORE)) {
            player.sendMessage(plugin.color("&cYou can only join groups in your same mode."));
            openJoinMenu(player);
            return;
        }

        requestJoin(player, target);
        openMainMenu(player);
    }

    public boolean requestJoin(Player requester, Player owner) {
        if (requester.getUniqueId().equals(owner.getUniqueId())) {
            requester.sendMessage(plugin.color("&cYou are already in your own group."));
            return false;
        }

        if (!plugin.isRestrictedMode(requester) || !plugin.isRestrictedMode(owner)) {
            requester.sendMessage(plugin.color("&cBoth players must be in Ironman/Hardcore."));
            return false;
        }

        GamemodeType requesterMode = plugin.getPlayerModeService().getMode(requester.getUniqueId());
        GamemodeType ownerMode = plugin.getPlayerModeService().getMode(owner.getUniqueId());
        if (requesterMode != ownerMode) {
            requester.sendMessage(plugin.color("&cYou can only join groups of players in your same mode."));
            return false;
        }

        pendingJoinRequestsByOwner.put(owner.getUniqueId(), requester.getUniqueId());
        pendingJoinOwnerByRequester.put(requester.getUniqueId(), owner.getUniqueId());

        plugin.send(requester, "trade-group-request-sent", "%player%", owner.getName());
        plugin.send(owner, "trade-group-request-received", "%player%", requester.getName());
        owner.sendMessage(plugin.color("&7Use &e/agg group accept " + requester.getName() + " &7or &e/agg group decline " + requester.getName()));
        return true;
    }

    public boolean acceptJoinRequest(Player owner, String requesterName) {
        UUID requesterUuid = pendingJoinRequestsByOwner.get(owner.getUniqueId());
        if (requesterUuid == null) {
            owner.sendMessage(plugin.color("&cYou have no pending join requests."));
            return false;
        }

        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requester == null || !requester.isOnline()) {
            clearRequest(owner.getUniqueId(), requesterUuid);
            owner.sendMessage(plugin.color("&cThat requester is no longer online."));
            return false;
        }

        if (requesterName != null && !requesterName.isBlank() && !requester.getName().equalsIgnoreCase(requesterName)) {
            owner.sendMessage(plugin.color("&cPending request is from &e" + requester.getName() + "&c, not &e" + requesterName + "&c."));
            return false;
        }

        GamemodeType ownerMode = plugin.getPlayerModeService().getMode(owner.getUniqueId());
        GamemodeType requesterMode = plugin.getPlayerModeService().getMode(requester.getUniqueId());
        if (ownerMode != requesterMode || (ownerMode != GamemodeType.IRONMAN && ownerMode != GamemodeType.HARDCORE)) {
            clearRequest(owner.getUniqueId(), requesterUuid);
            owner.sendMessage(plugin.color("&cRequest is no longer valid due to mode change."));
            requester.sendMessage(plugin.color("&cYour join request is no longer valid."));
            return false;
        }

        plugin.getPlayerModeService().ensureTradeGroup(owner.getUniqueId(), ownerMode);
        String targetGroup = plugin.getPlayerModeService().getTradeGroup(owner.getUniqueId());
        plugin.getPlayerModeService().setTradeGroup(requester.getUniqueId(), targetGroup);

        clearRequest(owner.getUniqueId(), requesterUuid);
        plugin.send(requester, "trade-group-joined", "%group%", targetGroup, "%player%", owner.getName());
        plugin.send(owner, "trade-group-request-accepted", "%player%", requester.getName());
        plugin.send(owner, "trade-group-member-joined", "%group%", targetGroup, "%player%", requester.getName());
        return true;
    }

    public boolean declineJoinRequest(Player owner, String requesterName) {
        UUID requesterUuid = pendingJoinRequestsByOwner.get(owner.getUniqueId());
        if (requesterUuid == null) {
            owner.sendMessage(plugin.color("&cYou have no pending join requests."));
            return false;
        }

        Player requester = Bukkit.getPlayer(requesterUuid);
        String actualName = requester == null ? "that player" : requester.getName();
        if (requesterName != null && !requesterName.isBlank() && requester != null && !requester.getName().equalsIgnoreCase(requesterName)) {
            owner.sendMessage(plugin.color("&cPending request is from &e" + requester.getName() + "&c, not &e" + requesterName + "&c."));
            return false;
        }

        clearRequest(owner.getUniqueId(), requesterUuid);
        owner.sendMessage(plugin.color("&7Declined join request from &e" + actualName + "&7."));
        if (requester != null && requester.isOnline()) {
            plugin.send(requester, "trade-group-request-declined", "%player%", owner.getName());
        }
        return true;
    }

    private void clearRequest(UUID ownerUuid, UUID requesterUuid) {
        pendingJoinRequestsByOwner.remove(ownerUuid);
        pendingJoinOwnerByRequester.remove(requesterUuid);
    }

    private void startPrompt(Player player, PromptType type, String promptMessage) {
        pendingPrompt.put(player.getUniqueId(), type);
        player.closeInventory();
        player.sendMessage(plugin.color(promptMessage));
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PromptType type = pendingPrompt.get(uuid);
        if (type == null) {
            return;
        }

        event.setCancelled(true);
        String raw = event.getMessage().trim();
        if (raw.equalsIgnoreCase("cancel")) {
            pendingPrompt.remove(uuid);
            event.getPlayer().sendMessage(plugin.color("&7Group action cancelled."));
            return;
        }

        String normalized = normalizeGroupName(raw);
        if (normalized == null) {
            event.getPlayer().sendMessage(plugin.color("&cInvalid name. Use 3-24 chars: letters, numbers, underscore, dash."));
            return;
        }

        pendingPrompt.remove(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                return;
            }

            GamemodeType mode = plugin.getPlayerModeService().getMode(player.getUniqueId());
            if (mode != GamemodeType.IRONMAN && mode != GamemodeType.HARDCORE) {
                player.sendMessage(plugin.color("&cTrade groups are only used in Ironman and Hardcore."));
                return;
            }

            plugin.getPlayerModeService().setTradeGroup(player.getUniqueId(), normalized);
            if (type == PromptType.CREATE) {
                player.sendMessage(plugin.color("&aCreated/switched to group: &b" + normalized));
            } else {
                player.sendMessage(plugin.color("&aRenamed your group to: &b" + normalized));
            }
            openMainMenu(player);
        });
    }

    private void sendGroupInfo(Player viewer, Player subject) {
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
                .toList();

        viewer.sendMessage(plugin.color("&8&m--------------------------------"));
        viewer.sendMessage(plugin.color("&7Group: &b" + group));
        viewer.sendMessage(plugin.color("&7Mode: &f" + mode.prettyName()));
        viewer.sendMessage(plugin.color("&7Online members: &f" + String.join("&7, &f", members)));
        viewer.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
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
}