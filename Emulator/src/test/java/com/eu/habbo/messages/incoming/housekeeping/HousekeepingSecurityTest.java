package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HousekeepingSecurityTest {
    private static final Path BASE = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

    @Test
    void ipsAreMaskedToTheirFirstTwoParts() {
        assertEquals("93.45.x.x", HousekeepingPrivacy.maskIp("93.45.12.7"));
        assertEquals("2001:db8:x", HousekeepingPrivacy.maskIp("2001:db8:85a3::1"));
        assertEquals("", HousekeepingPrivacy.maskIp(null));
        assertEquals("", HousekeepingPrivacy.maskIp(" "));
        assertEquals("x", HousekeepingPrivacy.maskIp("localhost"));
        assertEquals("93.45.12.7", HousekeepingPrivacy.show("93.45.12.7", true));
        assertEquals("93.45.x.x", HousekeepingPrivacy.show("93.45.12.7", false));
    }

    @Test
    void privateDataNeedsItsPermissionAndEveryRevealIsAudited() throws Exception {
        String list = Files.readString(BASE.resolve("HousekeepingRequestListEvent.java"));
        String detail = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/outgoing/housekeeping/HousekeepingUserDetailComposer.java"));

        assertEquals("acc_hk_view_private", HousekeepingPrivacy.PERMISSION);
        assertTrue(list.contains("hasPermission(HousekeepingPrivacy.PERMISSION)"));
        assertTrue(list.contains("\"user.view_private\""), "a reveal is written to the audit log");
        assertTrue(detail.contains("HousekeepingPrivacy.maskIp("), "the user detail never carries the IP in clear");
    }

    @Test
    void panelAccessAndPermissionEditingCanNeverBeLeftWithNobody() throws Exception {
        String permission = Files.readString(BASE.resolve("HousekeepingSetPermissionEvent.java"));
        String rank = Files.readString(BASE.resolve("HousekeepingSetUserRankEvent.java"));

        assertEquals(Set.of("acc_housekeeping", "cmd_update_permissions"), HousekeepingLockoutGuard.PROTECTED_KEYS);
        assertTrue(permission.contains("HousekeepingLockoutGuard.permissionChangeLocksOut("));
        assertTrue(rank.contains("HousekeepingLockoutGuard.rankChangeLocksOut("));
    }

    @Test
    void onlyTheHighestRankSwitchesTheLockdownAndItSurvivesARestart() throws Exception {
        String toggle = Files.readString(BASE.resolve("HousekeepingLockdownEvent.java"));

        assertTrue(toggle.contains("HousekeepingTargetRankGuard.isTopRank(this.client.getHabbo())"));
        assertEquals("housekeeping.lockdown", HousekeepingLockdown.SETTING_KEY);
        assertTrue(HousekeepingLockdown.WRITE_SQL.contains("emulator_settings"));
    }
}
