package polaris.database.integrity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrityAuditPolicyTest {
    @Test
    void warnReportsWithoutBlockingAndStrictFailsClosed() {
        IntegrityFinding finding = new IntegrityFinding(
                "users-badges.user",
                IntegrityFindingType.ORPHAN,
                IntegrityCheckSource.LOGICAL_CONTRACT,
                1,
                1,
                List.of(new IntegritySample(Map.of("user_id", "999"), 1)),
                "badge owner must exist");
        IntegrityAuditReport report = new IntegrityAuditReport(1, 40, 10, 12, List.of(finding), List.of());

        assertDoesNotThrow(() -> DatabaseIntegrityAudit.enforce(report, IntegrityAuditMode.WARN));
        assertThrows(
                IntegrityAuditException.class, () -> DatabaseIntegrityAudit.enforce(report, IntegrityAuditMode.STRICT));
    }
}
