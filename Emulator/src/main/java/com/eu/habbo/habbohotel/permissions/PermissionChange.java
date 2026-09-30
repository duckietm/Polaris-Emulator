package com.eu.habbo.habbohotel.permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One difference a permission reload found: a key whose value changed for a rank, or a rank that
 * appeared or went away. Ranks and keys are edited in the database, so comparing the loaded sets
 * is the only place such an edit can be seen and written to the audit.
 */
public record PermissionChange(int rankId, String rankName, String key, String oldValue, String newValue) {
    /** Stands for a rank or key that is not there. */
    public static final String NONE = "-";
    /** The key of a change that is about the rank itself. */
    public static final String RANK = "(rank)";

    /** What a rank looked like at one load: its name and its permission map. */
    public record RankState(String name, Map<String, Permission> permissions) {}

    public static Map<Integer, RankState> capture(Iterable<Rank> ranks) {
        Map<Integer, RankState> state = new TreeMap<>();

        for (Rank rank : ranks) {
            if (rank != null) {
                state.put(rank.getId(), new RankState(rank.getName(), rank.getPermissions()));
            }
        }

        return state;
    }

    public static List<PermissionChange> diff(Map<Integer, RankState> before, Map<Integer, RankState> after) {
        List<PermissionChange> changes = new ArrayList<>();
        TreeSet<Integer> rankIds = new TreeSet<>(before.keySet());
        rankIds.addAll(after.keySet());

        for (int rankId : rankIds) {
            RankState old = before.get(rankId);
            RankState now = after.get(rankId);

            if (old == null) {
                changes.add(new PermissionChange(rankId, now.name(), RANK, NONE, now.name()));
                continue;
            }

            if (now == null) {
                changes.add(new PermissionChange(rankId, old.name(), RANK, old.name(), NONE));
                continue;
            }

            TreeSet<String> keys = new TreeSet<>(old.permissions().keySet());
            keys.addAll(now.permissions().keySet());

            for (String key : keys) {
                String oldValue = valueOf(old.permissions().get(key));
                String newValue = valueOf(now.permissions().get(key));

                if (!oldValue.equals(newValue)) {
                    changes.add(new PermissionChange(rankId, now.name(), key, oldValue, newValue));
                }
            }
        }

        return changes;
    }

    private static String valueOf(Permission permission) {
        return permission == null ? NONE : permission.setting.name();
    }
}
