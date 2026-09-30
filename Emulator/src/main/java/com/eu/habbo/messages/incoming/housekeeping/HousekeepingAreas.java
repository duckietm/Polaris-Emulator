package com.eu.habbo.messages.incoming.housekeeping;

/**
 * The areas of the panel, each behind its own permission on top of acc_housekeeping, so a
 * hotel can give a moderator users and bans without economy or permissions. Handlers name
 * theirs through HousekeepingHandler.requiredPermission(); lists by their key.
 */
final class HousekeepingAreas {
    static final String USERS = "acc_hk_users";
    static final String BANS = "acc_hk_bans";
    static final String ECONOMY = "acc_hk_economy";
    static final String ROOMS = "acc_hk_rooms";
    static final String PERMISSIONS = "acc_hk_permissions";
    static final String HOTEL = "acc_hk_hotel";

    private HousekeepingAreas() {}

    /** The area a list belongs to, or null for the overview lists every operator sees. */
    static String forList(String listKey) {
        if (listKey == null) return null;
        if (listKey.startsWith("user.")) return USERS;
        if (listKey.startsWith("room.")) return ROOMS;

        return switch (listKey) {
            case "hotel.bans" -> BANS;
            case "hotel.permissions" -> PERMISSIONS;
            case "hotel.wordfilter", "hotel.security" -> HOTEL;
            default -> null;
        };
    }
}
