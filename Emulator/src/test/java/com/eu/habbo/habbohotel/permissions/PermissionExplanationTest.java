package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionExplanationTest {
    private static Rank rank(String key, PermissionSetting setting) {
        Rank rank = new Rank(5);
        if (key != null) {
            rank.setPermission(key, setting);
        }
        return rank;
    }

    private static String result(List<String> lines) {
        return lines.get(lines.size() - 1);
    }

    @Test
    void anAllowingRankNeedsNoPlugin() {
        List<String> lines = PermissionExplanation.explain(
                "Alice",
                "cmd_alert",
                rank("cmd_alert", PermissionSetting.ALLOWED),
                true,
                PermissionExplanation.PluginCheck.NOT_NEEDED);

        assertTrue(lines.contains("Rank value: 1 (allowed)"));
        assertEquals("Result: ALLOWED", result(lines));
    }

    @Test
    void aPluginCanGrantWhatTheRankDenies() {
        List<String> lines = PermissionExplanation.explain(
                "Alice",
                "cmd_alert",
                rank("cmd_alert", PermissionSetting.DISALLOWED),
                true,
                PermissionExplanation.PluginCheck.GRANTED);

        assertEquals("Result: ALLOWED (by a plugin)", result(lines));
    }

    @Test
    void roomOwnerValuesAndUnknownKeysAreExplained() {
        assertEquals(
                "Result: ALLOWED only where the user has room rights",
                result(PermissionExplanation.explain(
                        "Alice",
                        "acc_placefurni",
                        rank("acc_placefurni", PermissionSetting.ROOM_OWNER),
                        true,
                        PermissionExplanation.PluginCheck.REFUSED)));

        List<String> typo = PermissionExplanation.explain(
                "Alice", "acc_no_mute", rank(null, null), false, PermissionExplanation.PluginCheck.NOT_ASKED_OFFLINE);

        assertTrue(typo.contains("Rank value: not set for this rank (denied)"));
        assertTrue(typo.stream().anyMatch(line -> line.startsWith("Warning: no rank defines this key")));
        assertEquals("Result: DENIED", result(typo));
    }

    @Test
    void anAuditRowReadsAsOneLine() {
        String line = PermissionExplanation.line(new PermissionAuditLog.Entry(
                0,
                7,
                "Admin",
                PermissionAuditLog.RANK_SET,
                PermissionAuditLog.TARGET_USER,
                12,
                "rank",
                "1",
                "5",
                "give_rank"));

        assertTrue(line.endsWith("Admin: user 12 rank 1 -> 5 (give_rank)"), line);
        assertEquals(64, PermissionAuditLog.clip("x".repeat(80)).length());
    }
}
