package com.eu.habbo.database.backup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseBackupRequestTest {

    @Test
    void derivesTheExactMigrationTargetFromTheRuntimeJdbcConnection() {
        DatabaseBackupRequest request = DatabaseBackupRequest.fromJdbc(
                "jdbc:mariadb://db.internal:3307/polaris_prod?sslMode=verify-full",
                "runtime-user",
                "runtime-secret");

        assertEquals("db.internal", request.host());
        assertEquals(3307, request.port());
        assertEquals("polaris_prod", request.database());
        assertEquals("runtime-user", request.username());
        assertEquals("runtime-secret", request.password());
    }

    @Test
    void usesMariaDbDefaultPortAndSupportsIpv6() {
        DatabaseBackupRequest request = DatabaseBackupRequest.fromJdbc(
                "jdbc:mariadb://[2001:db8::7]/polaris",
                "user",
                "");

        assertEquals("[2001:db8::7]", request.host());
        assertEquals(3306, request.port());
        assertEquals("polaris", request.database());
    }

    @Test
    void rejectsUnsupportedOrIncompleteJdbcTargets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DatabaseBackupRequest.fromJdbc(
                        "jdbc:mysql://localhost/polaris", "user", "secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> DatabaseBackupRequest.fromJdbc(
                        "jdbc:mariadb:loadbalance://db1,db2/polaris", "user", "secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> DatabaseBackupRequest.fromJdbc(
                        "jdbc:mariadb://localhost", "user", "secret"));
    }
}
