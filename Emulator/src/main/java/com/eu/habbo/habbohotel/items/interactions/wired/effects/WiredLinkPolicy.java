package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Locale;
import java.util.Set;

/**
 * Which in-client destinations a wired box may push at the people in a room.
 *
 * <p>The open-pages effect took whatever string was typed and handed it to the client untouched, and
 * it is sold at rank 1, so any room owner could aim any navigation at every visitor. This bounds it
 * the way {@link WiredCommandPolicy} bounds the command effect: a fixed set of destinations anyone
 * may open, everything else behind {@code ACC_SUPERWIRED}.
 *
 * <p>The list is the set of link namespaces the client itself creates through {@code CreateLinkEvent},
 * minus the staff surfaces and the internal pickers - a wired box has no business opening housekeeping,
 * the mod tools, the furni or floor editor, or a chooser that only makes sense mid-flow.
 */
final class WiredLinkPolicy {

    private static final String RESTRICT_KEY = "hotel.wired.link.restrict_namespaces";

    private static final Set<String> SAFE_NAMESPACES = Set.of(
            "achievements",
            "avatar-editor",
            "avatar-effects",
            "badge-creator",
            "badge-leaderboard",
            "camera",
            "catalog",
            "chat-history",
            "customize",
            "fortune-wheel",
            "friends",
            "friends-messenger",
            "games",
            "group-members",
            "groupforum",
            "groups",
            "habbo",
            "habbopages",
            "help",
            "inventory",
            "mentions",
            "navigator",
            "soundboard",
            "stories",
            "translation-settings",
            "trax-editor",
            "user-account-settings",
            "user-settings",
            "wired-tools");

    private WiredLinkPolicy() {}

    /** Trims the link and drops a leading slash, so "/catalog/open" and "catalog/open" agree. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }

        return value;
    }

    /** The part before the first slash, lowercased - what decides where the client goes. */
    static String namespaceOf(String link) {
        String value = normalize(link);
        if (value.isEmpty()) {
            return "";
        }

        int separator = value.indexOf('/');
        return (separator < 0 ? value : value.substring(0, separator)).toLowerCase(Locale.ROOT);
    }

    static boolean isSafe(String link) {
        return SAFE_NAMESPACES.contains(namespaceOf(link));
    }

    /**
     * True when this configurer may point a box at this destination. Anyone may use the safe
     * namespaces; anything else - a staff surface, or a namespace this client does not know - needs
     * the same permission the other value-bearing wired boxes ask for.
     */
    static boolean canUse(String link, Habbo principal) {
        if (normalize(link).isEmpty()) {
            return false;
        }

        if (!restrictsNamespaces() || isSafe(link)) {
            return true;
        }

        return principal != null && principal.hasPermission(Permission.ACC_SUPERWIRED);
    }

    private static boolean restrictsNamespaces() {
        ConfigurationManager config = Emulator.getConfig();
        return config == null || config.getBoolean(RESTRICT_KEY, true);
    }
}
