package dev.ag.gamemode.model;

public enum GamemodeType {
    TRAVELER,
    IRONMAN,
    HARDCORE;

    public static GamemodeType fromString(String raw) {
        if (raw == null) {
            return null;
        }

        for (GamemodeType value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }

        return null;
    }

    public String prettyName() {
        String lower = name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
