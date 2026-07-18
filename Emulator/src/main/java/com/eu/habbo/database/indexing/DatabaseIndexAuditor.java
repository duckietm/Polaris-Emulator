package com.eu.habbo.database.indexing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class DatabaseIndexAuditor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseIndexAuditor.class);

    private final Connection connection;
    private final IndexContract contract;

    public DatabaseIndexAuditor(Connection connection, IndexContract contract) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.contract = Objects.requireNonNull(contract, "contract");
    }

    public static void auditAtStartup(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        try (Connection connection = dataSource.getConnection()) {
            IndexContract contract = IndexContractLoader.load(
                    DatabaseIndexAuditor.class.getClassLoader());
            IndexAuditReport report = new DatabaseIndexAuditor(connection, contract).audit();
            if (report.isComplete()) {
                LOGGER.info(
                        "Database indexes -> validated {} query-driven requirements",
                        report.coveredRequirements());
            } else {
                LOGGER.warn(
                        "Database indexes -> {}/{} requirements covered; missing={}",
                        report.coveredRequirements(),
                        report.requiredIndexes(),
                        report.missingRequirements());
            }
            if (!report.redundantCandidates().isEmpty()) {
                LOGGER.info(
                        "Database indexes -> redundant candidates (never removed automatically): {}",
                        report.redundantCandidates());
            }
        } catch (SQLException | RuntimeException error) {
            LOGGER.warn("Database index audit could not be completed", error);
        }
    }

    public IndexAuditReport audit() {
        try {
            Map<String, List<ActualIndex>> indexesByTable = loadIndexes();
            List<String> missing = new ArrayList<>();
            int covered = 0;
            for (IndexRequirement requirement : contract.requirements()) {
                boolean present = indexesByTable.getOrDefault(requirement.table(), List.of()).stream()
                        .anyMatch(index -> startsWith(index.columns(), requirement.columns()));
                if (present) covered++;
                else missing.add(requirement.displayName());
            }

            List<String> redundant = redundantCandidates(indexesByTable);
            missing.sort(String::compareTo);
            redundant.sort(String::compareTo);
            return new IndexAuditReport(
                    contract.requirements().size(), covered, missing, redundant);
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to audit database indexes", error);
        }
    }

    private Map<String, List<ActualIndex>> loadIndexes() throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> tables = new LinkedHashSet<>();
        for (IndexRequirement requirement : contract.requirements()) tables.add(requirement.table());

        Map<String, List<ActualIndex>> result = new LinkedHashMap<>();
        for (String table : tables) {
            Map<String, MutableIndex> tableIndexes = new TreeMap<>();
            try (ResultSet rows = metadata.getIndexInfo(
                    connection.getCatalog(), null, table, false, false)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME");
                    String column = rows.getString("COLUMN_NAME");
                    if (name == null || column == null) continue;
                    String normalizedName = normalize(name);
                    MutableIndex index = tableIndexes.get(normalizedName);
                    if (index == null) {
                        index = new MutableIndex(normalizedName, !rows.getBoolean("NON_UNIQUE"));
                        tableIndexes.put(normalizedName, index);
                    }
                    index.columns.put(rows.getShort("ORDINAL_POSITION"), normalize(column));
                }
            }
            List<ActualIndex> indexes = tableIndexes.values().stream()
                    .map(MutableIndex::freeze)
                    .filter(index -> !index.columns().isEmpty())
                    .toList();
            result.put(table, indexes);
        }
        return result;
    }

    private static List<String> redundantCandidates(Map<String, List<ActualIndex>> indexesByTable) {
        List<String> candidates = new ArrayList<>();
        indexesByTable.forEach((table, indexes) -> {
            for (ActualIndex shorter : indexes) {
                if (shorter.unique()) continue;
                ActualIndex covering = indexes.stream()
                        .filter(index -> !index.unique())
                        .filter(index -> index.columns().size() > shorter.columns().size())
                        .filter(index -> startsWith(index.columns(), shorter.columns()))
                        .min(Comparator.comparingInt((ActualIndex index) -> index.columns().size())
                                .thenComparing(ActualIndex::name))
                        .orElse(null);
                if (covering != null) {
                    candidates.add(table + "." + shorter.name()
                            + " is covered by " + covering.name());
                }
            }
        });
        return candidates;
    }

    private static boolean startsWith(List<String> actual, List<String> required) {
        if (actual.size() < required.size()) return false;
        for (int index = 0; index < required.size(); index++) {
            if (!actual.get(index).equals(required.get(index))) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record ActualIndex(String name, boolean unique, List<String> columns) {
    }

    private static final class MutableIndex {
        private final String name;
        private final boolean unique;
        private final Map<Short, String> columns = new TreeMap<>();

        private MutableIndex(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        private ActualIndex freeze() {
            return new ActualIndex(name, unique, List.copyOf(columns.values()));
        }
    }
}
