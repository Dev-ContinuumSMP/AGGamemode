package dev.ag.gamemode.model;

public enum HardcoreDeathAction {
    BAN,
    SPECTATOR,
    DEMOTE;

    public static HardcoreDeathAction fromString(String raw) {
        if (raw == null) {
            return BAN;
        }

        for (HardcoreDeathAction value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }

        return BAN;
    }
}
