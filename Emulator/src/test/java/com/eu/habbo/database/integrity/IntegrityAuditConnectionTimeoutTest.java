package com.eu.habbo.database.integrity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The integrity audit ran on a pooled connection whose socket gave up after 30 s while its queries may take up to
 * 300 s, and the pool reported the audit's connection as a leak. The pool also set MySQL driver options the MariaDB
 * driver does not know.
 */
class IntegrityAuditConnectionTimeoutTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/database/" + path));
    }

    @Test
    void theAuditConnectionOutlivesItsLongestQuery() throws Exception {
        String audit = source("integrity/DatabaseIntegrityAudit.java");

        assertTrue(audit.contains("(settings.queryTimeoutSeconds() + AUDIT_SOCKET_MARGIN_SECONDS) * 1000"));
        int disable = audit.indexOf("pool.setLeakDetectionThreshold(0);");
        int restore = audit.indexOf("pool.setLeakDetectionThreshold(leakThreshold);");
        assertTrue(disable > 0 && restore > disable);
        assertTrue(audit.lastIndexOf("} finally {", restore) > disable);
    }

    @Test
    void thePoolSetsOnlyOptionsTheMariaDbDriverKnows() throws Exception {
        String pool = source("DatabasePool.java");

        for (String unknown : new String[] {
            "\"rewriteBatchedStatements\"", "\"prepStmtCacheSqlLimit\"", "\"cacheServerConfiguration\"",
            "\"cacheResultSetMetadata\"", "\"elideSetAutoCommits\"", "\"maintainTimeStats\""
        }) {
            assertFalse(pool.contains(unknown), unknown);
        }
        assertTrue(pool.contains("addDataSourceProperty(\"socketTimeout\", \"30000\")"));
    }
}
