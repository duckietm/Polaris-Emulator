package com.eu.habbo.database.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MariaDbSqlCompatibilityAuditTest {
    @Test
    void detectsKnownCrossDialectConstructsWithLocations() {
        String sql = "SELECT * FROM users WHERE name ILIKE ? ORDER BY id NULLS LAST; "
                + "INSERT OR IGNORE INTO users(id) VALUES (1); "
                + "SELECT ROW_NUMBER() OVER (ORDER BY id) AS row_number FROM users;";

        var findings = MariaDbSqlCompatibilityAudit.scan("fixture.sql", sql);

        assertEquals(4, findings.size());
        assertTrue(findings.stream().allMatch(finding -> finding.location().startsWith("fixture.sql:")));
    }

    @Test
    void acceptsMariaDbFormsUsedByPolaris() {
        String sql = "INSERT IGNORE INTO users(id) VALUES (1); "
                + "INSERT INTO users_currency(user_id, type, amount) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount); "
                + "SELECT ROW_NUMBER() OVER (ORDER BY id) AS `row_number` FROM users;";

        assertTrue(MariaDbSqlCompatibilityAudit.scan("fixture.sql", sql).isEmpty());
    }

    @Test
    void productionQueriesAndManagedMigrationsPassTheMariaDbAudit() throws Exception {
        var findings = MariaDbSqlCompatibilityAudit.scanRepository(Path.of(".."));

        assertTrue(findings.isEmpty(), () -> "MariaDB-incompatible SQL found:\n" + String.join("\n", findings.stream()
                .map(Object::toString)
                .toList()));
    }
}
