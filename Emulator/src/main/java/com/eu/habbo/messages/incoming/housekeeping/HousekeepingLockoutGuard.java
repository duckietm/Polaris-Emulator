package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the hotel from locking itself out of housekeeping: no change may leave nobody holding
 * panel access or the permission to edit permissions. It checks the ranks (permission_definitions)
 * and the users on them; a check it cannot make refuses the change.
 */
final class HousekeepingLockoutGuard {
    /** The keys that must always stay with at least one user. */
    static final Set<String> PROTECTED_KEYS = Set.of("acc_housekeeping", "cmd_update_permissions");

    static final String DEFINITION_SQL = "SELECT * FROM permission_definitions WHERE permission_key = ? LIMIT 1";

    private HousekeepingLockoutGuard() {}

    /** Whether setting {@code key} to {@code value} on {@code rankId} would leave no user holding it. */
    static boolean permissionChangeLocksOut(String key, int rankId, int value) {
        if (!PROTECTED_KEYS.contains(key) || value > 0) return false;

        List<Integer> holders = ranksHolding(key);
        holders.remove(Integer.valueOf(rankId));

        return usersOnRanks(holders, 0) == 0;
    }

    /** Whether moving {@code userId} from {@code fromRankId} to {@code toRankId} leaves no user with panel access. */
    static boolean rankChangeLocksOut(int userId, int fromRankId, int toRankId) {
        for (String key : PROTECTED_KEYS) {
            List<Integer> holders = ranksHolding(key);

            if (holders.contains(fromRankId) && !holders.contains(toRankId) && usersOnRanks(holders, userId) == 0) {
                return true;
            }
        }

        return false;
    }

    /** The rank ids whose column for {@code key} is above 0. */
    static List<Integer> ranksHolding(String key) {
        return SqlQueries.queryOne(
                        DEFINITION_SQL,
                        set -> {
                            ResultSetMetaData meta = set.getMetaData();
                            List<Integer> ranks = new ArrayList<>();

                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                String column = meta.getColumnName(i).toLowerCase();

                                if (!column.startsWith("rank_")) continue;

                                try {
                                    int rankId = Integer.parseInt(column.substring("rank_".length()));

                                    if (set.getInt(i) > 0) ranks.add(rankId);
                                } catch (NumberFormatException ignored) {
                                    // not a rank column
                                }
                            }

                            return ranks;
                        },
                        key)
                .orElseGet(ArrayList::new);
    }

    /** Users on these ranks, leaving out {@code exceptUserId} (0 leaves out nobody). */
    static int usersOnRanks(List<Integer> rankIds, int exceptUserId) {
        if (rankIds.isEmpty()) return 0;

        String in = rankIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        // The ids are ints read from the schema, never input, so they are safe in the list.
        return SqlQueries.queryOne(
                        "SELECT COUNT(*) AS users FROM users WHERE `rank` IN (" + in + ") AND id <> ?",
                        set -> set.getInt("users"),
                        exceptUserId)
                .orElse(0);
    }
}
