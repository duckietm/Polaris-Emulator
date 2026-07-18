package com.eu.habbo.database.integrity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class IntegritySql {
    private IntegritySql() {
    }

    static String relationCount(RelationRequirement requirement) {
        return "SELECT COUNT(*) " + relationFromAndWhere(requirement);
    }

    static String relationSamples(RelationRequirement requirement, int limit) {
        requireLimit(limit);
        String selected = requirement.childColumns().stream()
                .map(column -> "c." + quote(column))
                .collect(Collectors.joining(", "));
        String order = requirement.childColumns().stream()
                .map(column -> "c." + quote(column))
                .collect(Collectors.joining(", "));
        return "SELECT " + selected + ' ' + relationFromAndWhere(requirement)
                + " ORDER BY " + order + " LIMIT " + limit;
    }

    static String duplicateCount(DuplicateRequirement requirement) {
        String columns = quoted(requirement.columns());
        return "SELECT COUNT(*) AS duplicate_groups, "
                + "COALESCE(SUM(group_size - 1), 0) AS affected_rows FROM ("
                + "SELECT COUNT(*) AS group_size FROM " + quote(requirement.table())
                + " GROUP BY " + columns + " HAVING COUNT(*) > 1) duplicate_sets";
    }

    static String duplicateSamples(DuplicateRequirement requirement, int limit) {
        requireLimit(limit);
        String columns = quoted(requirement.columns());
        return "SELECT " + columns + ", COUNT(*) AS duplicate_count FROM "
                + quote(requirement.table()) + " GROUP BY " + columns
                + " HAVING COUNT(*) > 1 ORDER BY duplicate_count DESC, "
                + columns + " LIMIT " + limit;
    }

    private static String relationFromAndWhere(RelationRequirement requirement) {
        List<String> joins = new ArrayList<>();
        for (int index = 0; index < requirement.childColumns().size(); index++) {
            joins.add("c." + quote(requirement.childColumns().get(index))
                    + " = p." + quote(requirement.parentColumns().get(index)));
        }
        List<String> predicates = new ArrayList<>();
        requirement.childColumns().forEach(column ->
                predicates.add("c." + quote(column) + " IS NOT NULL"));
        requirement.ignoreZeroColumns().forEach(column ->
                predicates.add("c." + quote(column) + " <> 0"));
        predicates.add("p." + quote(requirement.parentColumns().getFirst()) + " IS NULL");
        return "FROM " + quote(requirement.childTable()) + " c LEFT JOIN "
                + quote(requirement.parentTable()) + " p ON " + String.join(" AND ", joins)
                + " WHERE " + String.join(" AND ", predicates);
    }

    private static String quoted(List<String> identifiers) {
        return identifiers.stream().map(IntegritySql::quote).collect(Collectors.joining(", "));
    }

    private static String quote(String identifier) {
        return '`' + IntegrityIdentifiers.identifier(identifier, "SQL identifier") + '`';
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Sample limit must be between 1 and 100");
        }
    }
}
