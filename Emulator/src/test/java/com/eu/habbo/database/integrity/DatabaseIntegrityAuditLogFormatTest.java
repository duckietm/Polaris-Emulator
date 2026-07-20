package com.eu.habbo.database.integrity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseIntegrityAuditLogFormatTest {
    @TempDir
    Path tempDir;

    @Test
    void findingLineIsCompactAndHumanReadable() {
        IntegrityFinding finding = new IntegrityFinding(
                "items.owner",
                IntegrityFindingType.ORPHAN,
                IntegrityCheckSource.LOGICAL_CONTRACT,
                280,
                280,
                List.of(sample(Map.of("user_id", "5"), 1), sample(Map.of("user_id", "5"), 1)),
                "Every item must retain an existing owner");

        assertEquals(
                "  [1/4] items.owner (orphan): 280 rows - Every item must retain an existing owner; e.g. user_id=5",
                DatabaseIntegrityAudit.findingLine(1, 4, finding));
    }

    @Test
    void identicalSamplesCollapseWithOccurrenceMultiplier() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("user_id", "2");
        values.put("badge_code", "ACH_PetRespectGiver3");

        String compact = DatabaseIntegrityAudit.compactSamples(List.of(
                sample(values, 12),
                sample(values, 12)));

        assertEquals("user_id=2 badge_code=ACH_PetRespectGiver3 x12", compact);
    }

    @Test
    void atMostThreeDistinctSamplesAreListed() {
        String compact = DatabaseIntegrityAudit.compactSamples(List.of(
                sample(Map.of("room_id", "1"), 1),
                sample(Map.of("room_id", "2"), 1),
                sample(Map.of("room_id", "3"), 1),
                sample(Map.of("room_id", "4"), 1)));

        assertEquals("room_id=1; room_id=2; room_id=3; ...", compact);
    }

    @Test
    void unhealthyReportIsWrittenAsJsonAndClearedAgain() throws Exception {
        Path reportPath = tempDir.resolve("logging").resolve("database-integrity-audit.json");
        IntegrityAuditReport report = new IntegrityAuditReport(
                1, 10, 5, 39,
                List.of(new IntegrityFinding(
                        "items.owner",
                        IntegrityFindingType.ORPHAN,
                        IntegrityCheckSource.LOGICAL_CONTRACT,
                        280,
                        280,
                        List.of(sample(Map.of("user_id", "5"), 1)),
                        "Every item must retain an existing owner")),
                List.of(new IntegrityAuditError("audit-startup", "boom")));

        assertTrue(DatabaseIntegrityAudit.writeReport(report, reportPath));

        String json = Files.readString(reportPath);
        assertTrue(json.contains("\"check\":\"items.owner\""));
        assertTrue(json.contains("\"type\":\"ORPHAN\""));
        assertTrue(json.contains("\"user_id\":\"5\""));
        assertTrue(json.contains("\"occurrences\":1"));
        assertTrue(json.contains("Every item must retain an existing owner"));
        assertTrue(json.contains("\"error\":\"boom\""));

        DatabaseIntegrityAudit.clearReport(reportPath);
        assertFalse(Files.exists(reportPath));
    }

    @Test
    void unwritableReportPathNeverThrows() throws Exception {
        Path blocking = tempDir.resolve("logging");
        Files.writeString(blocking, "a plain file where the directory should be");
        IntegrityAuditReport report = new IntegrityAuditReport(
                1, 1, 1, 1,
                List.of(new IntegrityFinding(
                        "items.owner",
                        IntegrityFindingType.ORPHAN,
                        IntegrityCheckSource.LOGICAL_CONTRACT,
                        1,
                        1,
                        List.of(sample(Map.of("user_id", "5"), 1)),
                        "Every item must retain an existing owner")),
                List.of());

        assertFalse(DatabaseIntegrityAudit.writeReport(
                report, blocking.resolve("database-integrity-audit.json")));
    }

    private static IntegritySample sample(Map<String, String> values, long occurrences) {
        return new IntegritySample(values, occurrences);
    }
}
