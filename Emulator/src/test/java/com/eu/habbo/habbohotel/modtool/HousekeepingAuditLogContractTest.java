package com.eu.habbo.habbohotel.modtool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingAuditLogContractTest {
    private static String auditLogSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/HousekeepingAuditLog.java"));
    }

    @Test
    void writerUsesActionLogSchemaReadByHousekeepingClient() throws Exception {
        String source = auditLogSource();

        assertTrue(source.contains("actor_id"), "housekeeping_log writer must persist actor_id");
        assertTrue(source.contains("actor_name"), "housekeeping_log writer must persist actor_name");
        assertTrue(source.contains("target_type"), "housekeeping_log writer must persist target_type");
        assertTrue(source.contains("target_id"), "housekeeping_log writer must persist target_id");
        assertTrue(source.contains("target_label"), "housekeeping_log writer must persist target_label");
        assertTrue(source.contains("success"), "housekeeping_log writer must persist success");
        assertFalse(source.contains("operator_id"), "housekeeping_log writer must not use the obsolete operator_id schema");
        assertFalse(source.contains("target_user_id"), "housekeeping_log writer must not use the obsolete target_user_id schema");
    }
}
