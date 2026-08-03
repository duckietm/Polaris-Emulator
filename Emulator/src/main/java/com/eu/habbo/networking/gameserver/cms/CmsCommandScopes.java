package com.eu.habbo.networking.gameserver.cms;

import com.eu.habbo.messages.command.CommandRegistry;
import java.util.Map;
import java.util.Set;

/**
 * Maps administrative commands to coarse permission groups and decides whether a
 * key's granted scopes authorize a given command.
 *
 * <p>A scope token is one of:
 * <ul>
 *   <li>{@code *} — every command (except always-denied ones below);</li>
 *   <li>{@code group:*} — every command in a group, e.g. {@code economy:*};</li>
 *   <li>{@code group:command} — a single command via its group, e.g. {@code users:setrank};</li>
 *   <li>{@code command} — a single command by bare key, e.g. {@code givecredits}.</li>
 * </ul>
 *
 * <p>The load-testing stress commands are <b>never</b> reachable over the CMS API,
 * regardless of scopes — they exist only for the allow-listed RCON listener.
 */
public final class CmsCommandScopes {

    /** Commands that must never be dispatched over the HTTP API, even for a {@code *} key. */
    public static final Set<String> ALWAYS_DENIED = Set.of("stressstart", "stressstatus", "stressstop");

    // Command (normalized key) -> permission group. Mirrors the categories in
    // docs/cms-api-reference.md. Commands absent here are still reachable via a
    // bare-key scope or the "*" wildcard.
    private static final Map<String, String> GROUPS = Map.ofEntries(
            // economy
            Map.entry("givecredits", "economy"),
            Map.entry("givepixels", "economy"),
            Map.entry("givepoints", "economy"),
            Map.entry("sendgift", "economy"),
            Map.entry("sendroombundle", "economy"),
            Map.entry("giveuserclothing", "economy"),
            Map.entry("modifysubscription", "economy"),
            // users
            Map.entry("updateuser", "users"),
            Map.entry("setmotto", "users"),
            Map.entry("setrank", "users"),
            Map.entry("changeusername", "users"),
            Map.entry("givebadge", "users"),
            Map.entry("progressachievement", "users"),
            Map.entry("giverespect", "users"),
            // moderation
            Map.entry("muteuser", "moderation"),
            Map.entry("disconnect", "moderation"),
            Map.entry("modticket", "moderation"),
            Map.entry("executecommand", "moderation"),
            // alerts
            Map.entry("alertuser", "alerts"),
            Map.entry("hotelalert", "alerts"),
            Map.entry("imagealertuser", "alerts"),
            Map.entry("imagehotelalert", "alerts"),
            Map.entry("staffalert", "alerts"),
            Map.entry("talkuser", "alerts"),
            // social & rooms
            Map.entry("friendrequest", "social"),
            Map.entry("ignoreuser", "social"),
            Map.entry("stalkuser", "social"),
            Map.entry("forwarduser", "social"),
            Map.entry("changeroomowner", "social"),
            // cache reloads
            Map.entry("updatecatalog", "cache"),
            Map.entry("updateitems", "cache"),
            Map.entry("updatewordfilter", "cache"),
            Map.entry("updatewheel", "cache"),
            Map.entry("updatesoundboard", "cache"));

    private CmsCommandScopes() {}

    /** The permission group for a command, or {@code null} if it has no mapped group. */
    public static String groupFor(String commandKey) {
        return GROUPS.get(CommandRegistry.normalize(commandKey));
    }

    public static boolean isAlwaysDenied(String commandKey) {
        return ALWAYS_DENIED.contains(CommandRegistry.normalize(commandKey));
    }

    /**
     * Whether {@code scopes} authorize {@code commandKey}. Always {@code false} for
     * {@link #ALWAYS_DENIED} commands.
     */
    public static boolean isAllowed(Set<String> scopes, String commandKey) {
        if (scopes == null || commandKey == null) {
            return false;
        }
        String key = CommandRegistry.normalize(commandKey);
        if (ALWAYS_DENIED.contains(key)) {
            return false;
        }
        if (scopes.contains("*") || scopes.contains(key)) {
            return true;
        }
        String group = GROUPS.get(key);
        if (group != null) {
            return scopes.contains(group + ":*") || scopes.contains(group + ":" + key);
        }
        return false;
    }
}
