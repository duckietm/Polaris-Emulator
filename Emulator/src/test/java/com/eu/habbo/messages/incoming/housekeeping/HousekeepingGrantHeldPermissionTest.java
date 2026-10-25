package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Setting a permission for a lower rank only checked that the rank was lower, not that the operator
 * held what they granted: an operator could give the rank below acc_infinite_credits, move an alt
 * onto it, and gain what their own rank did not have.
 */
class HousekeepingGrantHeldPermissionTest {

    private static final int OFF = PermissionSetting.DISALLOWED.ordinal();
    private static final int ON = PermissionSetting.ALLOWED.ordinal();
    private static final int OWN_ROOMS = PermissionSetting.ROOM_OWNER.ordinal();

    @Test
    void takingAPermissionAwayIsAlwaysAllowed() {
        assertTrue(HousekeepingSetPermissionEvent.holds(OFF, OFF));
        assertTrue(HousekeepingSetPermissionEvent.holds(OWN_ROOMS, OFF));
    }

    @Test
    void anOperatorGrantsOnlyWhatTheyHold() {
        assertFalse(HousekeepingSetPermissionEvent.holds(OFF, ON));
        assertFalse(HousekeepingSetPermissionEvent.holds(OFF, OWN_ROOMS));
        assertFalse(HousekeepingSetPermissionEvent.holds(OWN_ROOMS, ON), "own rooms is narrower than everywhere");
        assertTrue(HousekeepingSetPermissionEvent.holds(OWN_ROOMS, OWN_ROOMS));
        assertTrue(HousekeepingSetPermissionEvent.holds(ON, ON));
        assertTrue(HousekeepingSetPermissionEvent.holds(ON, OWN_ROOMS));
    }

    @Test
    void theOperatorSettingComesFromTheirRank() {
        Rank rank = mock(Rank.class);
        when(rank.getPermissions())
                .thenReturn(Map.of(
                        "acc_infinite_credits", new Permission("acc_infinite_credits", PermissionSetting.ALLOWED)));
        HabboInfo info = mock(HabboInfo.class);
        when(info.getRank()).thenReturn(rank);
        Habbo operator = mock(Habbo.class);
        when(operator.getHabboInfo()).thenReturn(info);

        assertEquals(ON, HousekeepingSetPermissionEvent.operatorSetting(operator, "acc_infinite_credits"));
        assertEquals(OFF, HousekeepingSetPermissionEvent.operatorSetting(operator, "cmd_ban"));
    }

    @Test
    void theHandlerChecksBeforeWriting() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingSetPermissionEvent.java"));

        int check = source.indexOf("!holds(operatorSetting(this.client.getHabbo(), permissionKey), value)");
        int write = source.indexOf("HousekeepingPermissionMatrix.set(permissionKey, rankId, value);");

        assertTrue(check > 0 && check < write, "the grant is checked before the matrix is written:\n" + source);
        assertTrue(
                source.contains("!HousekeepingTargetRankGuard.isTopRank(this.client.getHabbo())"),
                "the top rank is exempt");
    }
}
