package com.eu.habbo.habbohotel.permissions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The text :perm shows: why a user has a key or not, and the permission audit. */
public final class PermissionExplanation {
    /** How a plugin was consulted: not needed (the rank already allows), asked, or not asked. */
    public enum PluginCheck {
        NOT_NEEDED,
        GRANTED,
        REFUSED,
        NOT_ASKED_OFFLINE
    }

    private PermissionExplanation() {}

    public static List<String> explain(
            String username, String key, Rank rank, boolean definedByAnyRank, PluginCheck plugin) {
        List<String> lines = new ArrayList<>();
        lines.add("Permission check: " + username + " / " + key);

        if (rank == null) {
            lines.add("Rank: none loaded (denied)");
            lines.add("Result: DENIED");
            return lines;
        }

        lines.add("Rank: " + rank.getName() + " (id " + rank.getId() + ", level " + rank.getLevel() + ")");

        Permission permission = rank.getPermissions().get(key);
        PermissionSetting setting = permission == null ? null : permission.setting;

        lines.add("Rank value: " + describe(setting));

        if (!definedByAnyRank) {
            lines.add("Warning: no rank defines this key. A typo, or a key without a migration?");
        }

        lines.add("Plugins: "
                + switch (plugin) {
                    case NOT_NEEDED -> "not asked (the rank allows it)";
                    case GRANTED -> "a plugin grants it";
                    case REFUSED -> "no plugin grants it";
                    case NOT_ASKED_OFFLINE -> "not asked (the user is offline)";
                });

        String result;
        if (setting == PermissionSetting.ALLOWED) {
            result = "ALLOWED";
        } else if (plugin == PluginCheck.GRANTED) {
            result = "ALLOWED (by a plugin)";
        } else if (setting == PermissionSetting.ROOM_OWNER) {
            result = "ALLOWED only where the user has room rights";
        } else {
            result = "DENIED";
        }

        lines.add("Result: " + result);
        return lines;
    }

    static String describe(PermissionSetting setting) {
        if (setting == null) {
            return "not set for this rank (denied)";
        }

        return switch (setting) {
            case ALLOWED -> "1 (allowed)";
            case ROOM_OWNER -> "2 (only with room rights)";
            case DISALLOWED -> "0 (denied)";
        };
    }

    /** One audit row as a line: when, who, what, old -> new, how. */
    public static String line(PermissionAuditLog.Entry entry) {
        String when = new SimpleDateFormat("dd-MM-yyyy HH:mm").format(new Date(entry.timestamp() * 1000L));
        String target = PermissionAuditLog.TARGET_RANK.equals(entry.targetType())
                ? "rank " + entry.targetId() + " " + entry.subject()
                : "user " + entry.targetId() + " " + entry.subject();
        String actor = entry.actorName() == null || entry.actorName().isEmpty() ? "system" : entry.actorName();

        return when + " " + actor + ": " + target + " " + entry.oldValue() + " -> " + entry.newValue() + " ("
                + entry.via() + ")";
    }
}
