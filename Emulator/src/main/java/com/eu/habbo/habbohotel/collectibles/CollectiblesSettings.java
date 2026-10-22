package com.eu.habbo.habbohotel.collectibles;

import com.eu.habbo.core.ConfigurationManager;

/** The {@code hotel.collectibles.*} emulator settings, read when a packet needs them. */
public record CollectiblesSettings(
        boolean enabled,
        int emeraldType,
        int silverType,
        boolean deliverItems,
        int xpPerLevel,
        int transferFeeSilver,
        boolean mintingEnabled,
        boolean emeraldCreditEnabled,
        boolean avatarsEnabled) {
    public static final String ENABLED = "hotel.collectibles.enabled";
    public static final String EMERALD_TYPE = "hotel.collectibles.currency.emerald";
    public static final String SILVER_TYPE = "hotel.collectibles.currency.silver";
    public static final String DELIVER_ITEMS = "hotel.collectibles.deliver_items";
    public static final String XP_PER_LEVEL = "hotel.collectibles.level.xp_per_level";
    public static final String TRANSFER_FEE = "hotel.collectibles.transfer.fee_silver";
    public static final String MINTING = "hotel.collectibles.minting.enabled";
    public static final String EMERALD_CREDIT = "hotel.collectibles.emerald_credit.enabled";
    public static final String AVATARS = "hotel.collectibles.avatars.enabled";

    /** The settings before avatar collectibles: those are on whenever collectibles are. */
    public CollectiblesSettings(
            boolean enabled,
            int emeraldType,
            int silverType,
            boolean deliverItems,
            int xpPerLevel,
            int transferFeeSilver,
            boolean mintingEnabled,
            boolean emeraldCreditEnabled) {
        this(
                enabled,
                emeraldType,
                silverType,
                deliverItems,
                xpPerLevel,
                transferFeeSilver,
                mintingEnabled,
                emeraldCreditEnabled,
                true);
    }

    /** The defaults of the migration: everything off until {@code hotel.collectibles.enabled} is 1. */
    public static CollectiblesSettings defaults() {
        return new CollectiblesSettings(false, 1001, 1000, true, 1000, 10, true, true, true);
    }

    /** Avatar collectibles (the avatar editor's NFT tab): {@code hotel.collectibles.avatars.enabled} under the main switch. */
    public boolean avatarsActive() {
        return this.enabled && this.avatarsEnabled;
    }

    public static CollectiblesSettings from(ConfigurationManager config) {
        if (config == null) return defaults();
        return new CollectiblesSettings(
                config.getBoolean(ENABLED, false),
                config.getInt(EMERALD_TYPE, 1001),
                config.getInt(SILVER_TYPE, 1000),
                config.getBoolean(DELIVER_ITEMS, true),
                config.getInt(XP_PER_LEVEL, 1000),
                Math.max(0, config.getInt(TRANSFER_FEE, 10)),
                config.getBoolean(MINTING, true),
                config.getBoolean(EMERALD_CREDIT, true),
                config.getBoolean(AVATARS, true));
    }
}
