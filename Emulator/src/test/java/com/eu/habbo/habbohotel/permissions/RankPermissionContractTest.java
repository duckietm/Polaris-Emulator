package com.eu.habbo.habbohotel.permissions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankPermissionContractTest {

    @Test
    void missingPermissionsFailClosed() {
        Rank rank = new Rank(1);

        assertFalse(rank.hasPermission(null, false));
        assertFalse(rank.hasPermission("", false));
        assertFalse(rank.hasPermission("acc_supporttool", false));
    }

    @Test
    void roomOwnerPermissionOnlyPassesWithRoomRights() {
        Rank rank = new Rank(1);
        rank.setPermission("acc_placefurni", PermissionSetting.ROOM_OWNER);

        assertFalse(rank.hasPermission("acc_placefurni", false));
        assertTrue(rank.hasPermission("acc_placefurni", true));
    }

    @Test
    void allowedPermissionPassesWithoutRoomRights() {
        Rank rank = new Rank(1);
        rank.setPermission("acc_supporttool", PermissionSetting.ALLOWED);

        assertTrue(rank.hasPermission("acc_supporttool", false));
    }
}
