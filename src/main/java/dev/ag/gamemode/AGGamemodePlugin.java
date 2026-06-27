package dev.ag.gamemode;

import dev.ag.gamemode.listener.ChatListener;
import dev.ag.gamemode.listener.GroupManagementListener;
import dev.ag.gamemode.listener.HardcoreListener;
import dev.ag.gamemode.listener.ModeSelectionListener;
import dev.ag.gamemode.listener.RestrictionListener;
import dev.ag.gamemode.model.GamemodeType;
import dev.ag.gamemode.model.HardcoreDeathAction;
import dev.ag.gamemode.service.PlayerModeService;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AGGamemodePlugin extends JavaPlugin {

    private PlayerModeService playerModeService;
    private Set<String> blockedCommands;
    private FileConfiguration guiConfig;
    private GroupManagementListener groupManagementListener;
    private TradeCommand tradeCommandExecutor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("gui.yml", false);
        reloadLocalConfig();

        this.playerModeService = new PlayerModeService(this);
        if (!this.playerModeService.load()) {
            getLogger().severe("AGGamemode disabled because persistent storage could not be initialized.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.groupManagementListener = new GroupManagementListener(this);

        PluginCommand command = getCommand("aggamemode");
        if (command != null) {
            GamemodeCommand executor = new GamemodeCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        PluginCommand tradeCommand = getCommand("trade");
        if (tradeCommand != null) {
            this.tradeCommandExecutor = new TradeCommand(this);
            tradeCommand.setExecutor(tradeCommandExecutor);
            tradeCommand.setTabCompleter(tradeCommandExecutor);
        }

        Bukkit.getPluginManager().registerEvents(new RestrictionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new HardcoreListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ModeSelectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(groupManagementListener, this);

        getLogger().info("AGGamemode enabled.");
    }

    @Override
    public void onDisable() {
        if (playerModeService != null) {
            playerModeService.save();
        }
    }

    public void reloadLocalConfig() {
        reloadConfig();
        File guiFile = new File(getDataFolder(), "gui.yml");
        this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);

        List<String> configured = getConfig().getStringList("blocked-commands");
        this.blockedCommands = new HashSet<>();
        for (String entry : configured) {
            String clean = entry.toLowerCase(Locale.ROOT).replace("/", "").trim();
            if (!clean.isEmpty()) {
                blockedCommands.add(clean);
            }
        }
    }

    public PlayerModeService getPlayerModeService() {
        return playerModeService;
    }

    public GroupManagementListener getGroupManagementListener() {
        return groupManagementListener;
    }

    public boolean isInActiveTrade(Player player) {
        return tradeCommandExecutor != null && tradeCommandExecutor.isInActiveTrade(player);
    }

    public boolean hasActiveTradeBetween(Player first, Player second) {
        return tradeCommandExecutor != null && tradeCommandExecutor.hasActiveTradeBetween(first, second);
    }

    public void applyMode(Player player, GamemodeType mode) {
        GamemodeType previous = playerModeService.getMode(player.getUniqueId());
        boolean hadSelection = playerModeService.hasSelectedMode(player.getUniqueId());

        playerModeService.setMode(player.getUniqueId(), mode);
        if (mode == GamemodeType.IRONMAN || mode == GamemodeType.HARDCORE) {
            if (!hadSelection || previous != mode) {
                playerModeService.assignPersonalTradeGroup(player.getUniqueId(), mode);
            } else {
                playerModeService.ensureTradeGroup(player.getUniqueId(), mode);
            }
        } else {
            playerModeService.setTradeGroup(player.getUniqueId(), null);
        }
        syncModeGroups(player, mode);
    }

    public void syncModeGroups(Player player, GamemodeType mode) {
        if (!getConfig().getBoolean("groups.enabled", true)) {
            return;
        }

        String ironGroup = getConfig().getString("groups.iron-group", "iron");
        String hardcoreGroup = getConfig().getString("groups.hardcore-group", "hardcore");
        List<String> commands = getConfig().getStringList("groups.sync-commands");
        if (commands.isEmpty()) {
            commands = List.of(
                    "lp user %player% parent remove %iron_group%",
                    "lp user %player% parent remove %hardcore_group%",
                    "lp user %player% parent add %target_group%"
            );
        }

        String targetGroup = switch (mode) {
            case IRONMAN -> ironGroup;
            case HARDCORE -> hardcoreGroup;
            default -> "";
        };

        for (String rawCommand : commands) {
            if (rawCommand == null || rawCommand.isBlank()) {
                continue;
            }

            if (targetGroup.isBlank() && rawCommand.contains("%target_group%")) {
                continue;
            }

            String command = rawCommand
                    .replace("%player%", player.getName())
                    .replace("%mode%", mode.name().toLowerCase(Locale.ROOT))
                    .replace("%iron_group%", ironGroup)
                    .replace("%hardcore_group%", hardcoreGroup)
                    .replace("%target_group%", targetGroup)
                    .trim();

            if (!command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public boolean isRestrictedMode(Player player) {
        GamemodeType mode = playerModeService.getMode(player.getUniqueId());
        return mode == GamemodeType.IRONMAN || mode == GamemodeType.HARDCORE;
    }

    public boolean isBlockedCommand(String input) {
        String cleaned = input.toLowerCase(Locale.ROOT).trim();
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }

        String label = cleaned.split(" ")[0];
        if (isBlockedLabel(label)) {
            return true;
        }

        PluginCommand resolved = Bukkit.getPluginCommand(label);
        if (resolved == null && label.contains(":")) {
            String baseLabel = label.substring(label.lastIndexOf(':') + 1);
            resolved = Bukkit.getPluginCommand(baseLabel);
        }

        if (resolved != null) {
            String commandName = resolved.getName().toLowerCase(Locale.ROOT);
            if (blockedCommands.contains(commandName)) {
                return true;
            }
        }

        return false;
    }

    public boolean isTradeCommand(String input) {
        String[] parts = splitCommand(input);
        if (parts.length == 0) {
            return false;
        }

        String label = normalizeLabel(parts[0]);
        if (label.equals("trade")) {
            return true;
        }

        PluginCommand resolved = Bukkit.getPluginCommand(label);
        return resolved != null && resolved.getName().equalsIgnoreCase("trade");
    }

    public boolean canTradeWithinRestrictedGroup(Player requester, String input) {
        if (!isTradeCommand(input)) {
            return false;
        }

        String[] parts = splitCommand(input);
        if (parts.length < 2) {
            return false;
        }

        Player target = resolveTradeTarget(parts, requester);
        if (target == null) {
            return false;
        }

        return canPlayersTrade(requester, target);
    }

    public boolean canPlayersTrade(Player requester, Player target) {
        if (requester == null || target == null) {
            return false;
        }

        GamemodeType requesterMode = playerModeService.getMode(requester.getUniqueId());
        GamemodeType targetMode = playerModeService.getMode(target.getUniqueId());
        boolean requesterRestricted = requesterMode == GamemodeType.IRONMAN || requesterMode == GamemodeType.HARDCORE;
        boolean targetRestricted = targetMode == GamemodeType.IRONMAN || targetMode == GamemodeType.HARDCORE;

        return requesterRestricted
                && targetRestricted
                && requesterMode == targetMode
                && playerModeService.sharesTradeGroup(requester.getUniqueId(), target.getUniqueId());
    }

    public boolean isTradeManagementCommand(String input) {
        if (!isTradeCommand(input)) {
            return false;
        }

        String[] parts = splitCommand(input);
        if (parts.length < 2) {
            return false;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
        return sub.equals("accept") || sub.equals("deny") || sub.equals("decline") || sub.equals("cancel") || sub.equals("help");
    }

    public String rewriteTradeCommandIfNeeded(Player requester, String input) {
        if (!getConfig().getBoolean("trade.auto-rewrite-direct-command", true)) {
            return input;
        }

        String[] parts = splitCommand(input);
        if (parts.length != 2) {
            return input;
        }

        String label = normalizeLabel(parts[0]);
        if (!label.equals("trade")) {
            return input;
        }

        Player target = resolveTradeTarget(parts, requester);
        if (target == null) {
            return input;
        }

        String requestSubcommand = getConfig().getString("trade.request-subcommand", "request").trim();
        if (requestSubcommand.isEmpty()) {
            requestSubcommand = "request";
        }

        return "/" + label + " " + requestSubcommand + " " + target.getName();
    }

    private Player resolveTradeTarget(String[] parts, Player requester) {
        for (int i = 1; i < parts.length; i++) {
            String candidate = parts[i];
            if (candidate == null || candidate.isBlank()) {
                continue;
            }

            Player target = Bukkit.getPlayerExact(candidate);
            if (target == null) {
                target = Bukkit.getPlayer(candidate);
            }

            if (target != null && !target.getUniqueId().equals(requester.getUniqueId())) {
                return target;
            }
        }

        return null;
    }

    private String[] splitCommand(String input) {
        String cleaned = input == null ? "" : input.trim();
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            return new String[0];
        }
        return cleaned.split("\\s+");
    }

    private String normalizeLabel(String rawLabel) {
        String label = rawLabel.toLowerCase(Locale.ROOT);
        return label.contains(":") ? label.substring(label.lastIndexOf(':') + 1) : label;
    }

    private boolean isBlockedLabel(String label) {
        String baseLabel = label.contains(":") ? label.substring(label.lastIndexOf(':') + 1) : label;
        return blockedCommands.contains(label) || blockedCommands.contains(baseLabel);
    }

    public String getSymbol(GamemodeType mode) {
        String path = "symbols." + mode.name().toLowerCase(Locale.ROOT);
        return color(getConfig().getString(path, ""));
    }

    public HardcoreDeathAction getHardcoreDeathAction() {
        return HardcoreDeathAction.fromString(getConfig().getString("hardcore.death-action"));
    }

    public GamemodeType getHardcoreDemoteMode() {
        GamemodeType parsed = GamemodeType.fromString(getConfig().getString("hardcore.demote-mode"));
        if (parsed == null || parsed == GamemodeType.HARDCORE) {
            return GamemodeType.IRONMAN;
        }
        return parsed;
    }

    @SuppressWarnings("deprecation")
    public void handleHardcoreDeath(Player player) {
        HardcoreDeathAction action = getHardcoreDeathAction();

        if (action == HardcoreDeathAction.BAN) {
            String plainBanMessage = ChatColor.stripColor(color(getConfig().getString(
                    "hardcore.ban-message",
                    "Hardcore death is permanent."
            )));
            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), plainBanMessage, null, "AGGamemode");
            player.kickPlayer(plainBanMessage);
            return;
        }

        if (action == HardcoreDeathAction.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            send(player, "hardcore-death-ban");
            return;
        }

        GamemodeType demoteMode = getHardcoreDemoteMode();
        demoteHardcore(player);
    }

    @SuppressWarnings("deprecation")
    public void banHardcoreForDays(Player player, int days) {
        int safeDays = Math.max(1, days);
        String plainBanMessage = ChatColor.stripColor(color(getConfig().getString(
                "hardcore.temp-ban-message",
                "You died in Hardcore and are banned for %days% days."
        ).replace("%days%", String.valueOf(safeDays))));

        Date expiry = Date.from(Instant.now().plus(safeDays, ChronoUnit.DAYS));
        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), plainBanMessage, expiry, "AGGamemode");
        player.kickPlayer(plainBanMessage);
    }

    public void demoteHardcore(Player player) {
        GamemodeType demoteMode = getHardcoreDemoteMode();
        applyMode(player, demoteMode);
        send(player, "hardcore-demoted", "%mode%", demoteMode.prettyName());
    }

    public void send(Player player, String messageKey, String... replacements) {
        String message = getConfig().getString("messages." + messageKey, "");
        if (message == null || message.isEmpty()) {
            return;
        }

        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }

        String prefix = color(getConfig().getString("messages.prefix", ""));
        player.sendMessage(prefix + color(message));
    }

    public void broadcast(String messageKey, String... replacements) {
        String message = getConfig().getString("messages." + messageKey, "");
        if (message == null || message.isEmpty()) {
            return;
        }

        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }

        String prefix = color(getConfig().getString("messages.prefix", ""));
        Bukkit.broadcastMessage(prefix + color(message));
    }

    @SuppressWarnings("deprecation")
    public String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public List<String> getModeSuggestions() {
        List<String> modes = new ArrayList<>();
        for (GamemodeType value : GamemodeType.values()) {
            modes.add(value.name().toLowerCase(Locale.ROOT));
        }
        return modes;
    }
}
