package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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

    @Test
    void aReloadReplacesTheWholeSetAtOnce() {
        Rank rank = new Rank(1);
        rank.setPermission("acc_supporttool", PermissionSetting.ALLOWED);
        java.util.Map<String, Permission> before = rank.getPermissions();

        rank.replacePermissions(
                java.util.Map.of("acc_placefurni", new Permission("acc_placefurni", PermissionSetting.ALLOWED)));

        assertFalse(rank.hasPermission("acc_supporttool", false), "a key the reload no longer has is gone");
        assertTrue(rank.hasPermission("acc_placefurni", false));
        assertTrue(before.containsKey("acc_supporttool"), "a map handed out earlier is never changed under its reader");
    }

    @Test
    void settingOnePermissionKeepsTheOthers() {
        Rank rank = new Rank(1);
        rank.setPermission("acc_supporttool", PermissionSetting.ALLOWED);
        java.util.Map<String, Permission> before = rank.getPermissions();

        rank.setPermission("acc_placefurni", PermissionSetting.ALLOWED);

        assertTrue(rank.hasPermission("acc_supporttool", false));
        assertTrue(rank.hasPermission("acc_placefurni", false));
        assertFalse(before.containsKey("acc_placefurni"));
    }
}
