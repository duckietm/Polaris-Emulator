package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingListComposer;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The permission matrix: every permission_definitions row against every rank. The rank columns
 * are named "rank:&lt;id&gt;:&lt;editable 1|0&gt;:&lt;rank name&gt;" so a single list carries what the
 * panel needs to draw and lock the columns; editable follows the same rank policy as changing a
 * user's rank.
 */
final class HousekeepingPermissionMatrix {
    static final String RANKS_SQL = "SELECT id, rank_name FROM permission_ranks ORDER BY id";
    static final String DEFINITIONS_SQL = "SELECT * FROM permission_definitions ORDER BY permission_key";
    static final String MAX_VALUE_SQL = "SELECT max_value FROM permission_definitions WHERE permission_key = ? LIMIT 1";
    static final String RANK_EXISTS_SQL = "SELECT id FROM permission_ranks WHERE id = ? LIMIT 1";

    private record RankColumn(int id, String name) {}

    private HousekeepingPermissionMatrix() {}

    static String rankColumn(int rankId) {
        return "rank_" + rankId;
    }

    static HousekeepingListComposer list(String listKey, Habbo operator) {
        try {
            List<RankColumn> ranks =
                    SqlQueries.query(RANKS_SQL, set -> new RankColumn(set.getInt("id"), set.getString("rank_name")));
            List<String> columns = new ArrayList<>(List.of("permission", "comment", "max"));

            for (RankColumn rank : ranks) {
                boolean editable = HousekeepingTargetRankGuard.canTargetRank(operator, rank.id());
                columns.add("rank:" + rank.id() + ":" + (editable ? 1 : 0) + ":"
                        + (rank.name() == null ? "" : rank.name()));
            }

            List<List<String>> rows = SqlQueries.query(DEFINITIONS_SQL, set -> {
                ResultSetMetaData meta = set.getMetaData();
                Set<String> available = new HashSet<>();

                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    available.add(meta.getColumnName(i).toLowerCase());
                }

                List<String> row = new ArrayList<>();
                row.add(set.getString("permission_key"));
                row.add(set.getString("comment") == null ? "" : set.getString("comment"));
                row.add(String.valueOf(set.getInt("max_value")));

                for (RankColumn rank : ranks) {
                    String column = rankColumn(rank.id());
                    row.add(available.contains(column) ? String.valueOf(set.getInt(column)) : "0");
                }

                return row;
            });

            return new HousekeepingListComposer(listKey, 0, true, "", columns, rows);
        } catch (SqlQueries.DataAccessException e) {
            return HousekeepingListComposer.failure(listKey, 0, "housekeeping.list.failed");
        }
    }

    /** The highest value a permission takes, or empty when there is no such permission. */
    static Optional<Integer> maxValue(String permissionKey) {
        return SqlQueries.queryOne(MAX_VALUE_SQL, set -> set.getInt("max_value"), permissionKey);
    }

    static boolean rankExists(int rankId) {
        return SqlQueries.queryOne(RANK_EXISTS_SQL, set -> set.getInt("id"), rankId)
                .isPresent();
    }

    /** The rank id is validated against permission_ranks before it becomes a column name. */
    static int set(String permissionKey, int rankId, int value) {
        return SqlQueries.update(
                "UPDATE permission_definitions SET `" + rankColumn(rankId) + "` = ? WHERE permission_key = ?",
                value,
                permissionKey);
    }
}
