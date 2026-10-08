package com.eu.habbo.habbohotel.soundboard;

public record SoundboardCatalogCommand(
        int id, String name, String classname, String url, int minRank, boolean enabled, int cooldownSeconds) {

    /** Marks a command from a client that does not know pad cooldowns: an update keeps the stored value. */
    public static final int KEEP_COOLDOWN = -1;

    public SoundboardCatalogCommand(
            int id, String name, String classname, String url, int minRank, boolean enabled) {
        this(id, name, classname, url, minRank, enabled, KEEP_COOLDOWN);
    }
}
