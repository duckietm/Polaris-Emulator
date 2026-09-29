package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PermissionChangeTest {
    private static Map<String, Permission> perms(Object... keyAndSetting) {
        java.util.HashMap<String, Permission> map = new java.util.HashMap<>();
        for (int i = 0; i < keyAndSetting.length; i += 2) {
            String key = (String) keyAndSetting[i];
            map.put(key, new Permission(key, (PermissionSetting) keyAndSetting[i + 1]));
        }
        return map;
    }

    @Test
    void anUnchangedReloadHasNoChanges() {
        Map<Integer, PermissionChange.RankState> state =
                Map.of(1, new PermissionChange.RankState("Member", perms("cmd_dance", PermissionSetting.ALLOWED)));

        assertTrue(PermissionChange.diff(state, state).isEmpty());
    }

    @Test
    void findsChangedAddedAndRemovedKeys() {
        Map<Integer, PermissionChange.RankState> before = Map.of(
                5,
                new PermissionChange.RankState(
                        "Moderator",
                        perms(
                                "acc_supporttool", PermissionSetting.DISALLOWED,
                                "cmd_alert", PermissionSetting.ALLOWED)));
        Map<Integer, PermissionChange.RankState> after = Map.of(
                5,
                new PermissionChange.RankState(
                        "Moderator",
                        perms(
                                "acc_supporttool", PermissionSetting.ALLOWED,
                                "cmd_perm", PermissionSetting.ROOM_OWNER)));

        assertEquals(
                List.of(
                        new PermissionChange(5, "Moderator", "acc_supporttool", "DISALLOWED", "ALLOWED"),
                        new PermissionChange(5, "Moderator", "cmd_alert", "ALLOWED", PermissionChange.NONE),
                        new PermissionChange(5, "Moderator", "cmd_perm", PermissionChange.NONE, "ROOM_OWNER")),
                PermissionChange.diff(before, after));
    }

    @Test
    void findsRanksThatAppearedOrWentAway() {
        Map<Integer, PermissionChange.RankState> before = Map.of(3, new PermissionChange.RankState("Old", perms()));
        Map<Integer, PermissionChange.RankState> after = Map.of(4, new PermissionChange.RankState("New", perms()));

        assertEquals(
                List.of(
                        new PermissionChange(3, "Old", PermissionChange.RANK, "Old", PermissionChange.NONE),
                        new PermissionChange(4, "New", PermissionChange.RANK, PermissionChange.NONE, "New")),
                PermissionChange.diff(before, after));
    }
}
