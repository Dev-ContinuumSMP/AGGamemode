package dev.ag.gamemode.service;

import dev.ag.gamemode.model.GamemodeType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerModeService {

    private static final String DATABASE_BASENAME = "player_modes";

    private final JavaPlugin plugin;
    private final Map<UUID, GamemodeType> modeCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> tradeGroupCache = new ConcurrentHashMap<>();
    private Connection connection;

    public PlayerModeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
            return false;
        }

        File databaseFile = new File(plugin.getDataFolder(), DATABASE_BASENAME);
        String jdbcUrl = "jdbc:h2:file:" + databaseFile.getAbsolutePath().replace('\\', '/')
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        try {
            Class.forName("org.h2.Driver");
            this.connection = DriverManager.getConnection(jdbcUrl);
            createTable();
            loadFromDatabase();
            migrateLegacyYamlIfPresent();
            plugin.getLogger().info("H2 database ready: " + new File(plugin.getDataFolder(), DATABASE_BASENAME + ".mv.db").getAbsolutePath());
            return true;
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().severe("H2 driver is missing. Ensure plugin libraries are enabled and internet access is available for Paper library download.");
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to initialize H2 database: " + exception.getMessage());
        }

        return false;
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_modes (uuid VARCHAR(36) PRIMARY KEY, mode VARCHAR(16) NOT NULL, trade_group VARCHAR(64))";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            statement.execute("ALTER TABLE player_modes ADD COLUMN IF NOT EXISTS trade_group VARCHAR(64)");
        }
    }

    private void loadFromDatabase() throws SQLException {
        modeCache.clear();
        tradeGroupCache.clear();
        String sql = "SELECT uuid, mode, trade_group FROM player_modes";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String rawUuid = resultSet.getString("uuid");
                String rawMode = resultSet.getString("mode");
                String tradeGroup = resultSet.getString("trade_group");
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    GamemodeType mode = GamemodeType.fromString(rawMode);
                    modeCache.put(uuid, mode == null ? GamemodeType.TRAVELER : mode);
                    if (tradeGroup != null && !tradeGroup.isBlank()) {
                        tradeGroupCache.put(uuid, tradeGroup);
                    }
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid UUID in player_modes table: " + rawUuid);
                }
            }
        }
    }

    private void migrateLegacyYamlIfPresent() {
        File legacyFile = new File(plugin.getDataFolder(), "players.yml");
        if (!legacyFile.exists()) {
            return;
        }

        FileConfiguration legacyData = YamlConfiguration.loadConfiguration(legacyFile);
        if (legacyData.getConfigurationSection("players") == null) {
            return;
        }

        int migrated = 0;
        for (String uuidRaw : legacyData.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidRaw);
                if (hasSelectedMode(uuid)) {
                    continue;
                }

                GamemodeType mode = GamemodeType.fromString(legacyData.getString("players." + uuidRaw + ".mode"));
                setMode(uuid, mode == null ? GamemodeType.TRAVELER : mode);
                migrated++;
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid UUID in players.yml: " + uuidRaw);
            }
        }

        if (migrated > 0) {
            plugin.getLogger().info("Migrated " + migrated + " player mode entries from players.yml into H2.");
        }
    }

    public GamemodeType getMode(UUID uuid) {
        return modeCache.getOrDefault(uuid, GamemodeType.TRAVELER);
    }

    public boolean hasSelectedMode(UUID uuid) {
        return modeCache.containsKey(uuid);
    }

    public void setMode(UUID uuid, GamemodeType mode) {
        modeCache.put(uuid, mode);
        String currentTradeGroup = tradeGroupCache.get(uuid);
        if (connection == null) {
            plugin.getLogger().warning("Database connection is not available; could not persist mode for " + uuid + ".");
            return;
        }

        String sql = "MERGE INTO player_modes KEY(uuid) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, mode.name());
            statement.setString(3, currentTradeGroup);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to persist mode for " + uuid + ": " + exception.getMessage());
        }
    }

    public String getTradeGroup(UUID uuid) {
        return tradeGroupCache.get(uuid);
    }

    public void setTradeGroup(UUID uuid, String tradeGroup) {
        if (tradeGroup == null || tradeGroup.isBlank()) {
            tradeGroupCache.remove(uuid);
        } else {
            tradeGroupCache.put(uuid, tradeGroup.trim().toLowerCase());
        }
        persistPlayer(uuid);
    }

    public void assignPersonalTradeGroup(UUID uuid, GamemodeType mode) {
        String prefix = mode.name().toLowerCase();
        String groupId = prefix + "-" + uuid.toString().substring(0, 8);
        setTradeGroup(uuid, groupId);
    }

    public void ensureTradeGroup(UUID uuid, GamemodeType mode) {
        String current = getTradeGroup(uuid);
        if (current == null || current.isBlank()) {
            assignPersonalTradeGroup(uuid, mode);
        }
    }

    public boolean sharesTradeGroup(UUID first, UUID second) {
        String g1 = getTradeGroup(first);
        String g2 = getTradeGroup(second);
        return g1 != null && !g1.isBlank() && g1.equalsIgnoreCase(g2);
    }

    private void persistPlayer(UUID uuid) {
        GamemodeType mode = modeCache.getOrDefault(uuid, GamemodeType.TRAVELER);
        String tradeGroup = tradeGroupCache.get(uuid);

        if (connection == null) {
            plugin.getLogger().warning("Database connection is not available; could not persist player state for " + uuid + ".");
            return;
        }

        String sql = "MERGE INTO player_modes KEY(uuid) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, mode.name());
            statement.setString(3, tradeGroup);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to persist player state for " + uuid + ": " + exception.getMessage());
        }
    }

    public void save() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to close H2 database connection: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }
}
