package com.eu.habbo.networking.gameserver.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * /api/emustats showed every moderator the credits of everyone online and the room of each one, invisible staff and
 * users who block following included.
 */
class EmuStatsVisibilityTest {

    private static JsonObject user(int id, int roomId) {
        JsonObject user = new JsonObject();
        user.addProperty("id", id);
        user.addProperty("username", "user" + id);
        user.addProperty("rank", "Member");
        user.addProperty("credits", 1000 * id);
        user.addProperty("roomId", roomId);
        return user;
    }

    private static JsonArray users() {
        JsonArray users = new JsonArray();
        users.add(user(1, 10));
        users.add(user(2, 20));
        users.add(user(3, 30));
        return users;
    }

    @Test
    void hidesHiddenUsersTheRoomOfThoseWhoBlockFollowingAndEveryonesCredits() {
        JsonArray visible = EmuStatsHttpHandler.visibleUsers(users(), id -> id == 2, id -> id == 3);

        assertEquals(2, visible.size());
        JsonObject first = visible.get(0).getAsJsonObject();
        JsonObject third = visible.get(1).getAsJsonObject();
        assertEquals(1, first.get("id").getAsInt());
        assertEquals(10, first.get("roomId").getAsInt());
        assertEquals(3, third.get("id").getAsInt());
        assertEquals(0, third.get("roomId").getAsInt());
        assertFalse(first.has("credits"));
        assertFalse(third.has("credits"));
    }

    @Test
    void theSnapshotItselfIsNotChanged() {
        JsonArray users = users();

        EmuStatsHttpHandler.visibleUsers(users, id -> false, id -> true);

        assertTrue(users.get(0).getAsJsonObject().has("credits"));
        assertEquals(10, users.get(0).getAsJsonObject().get("roomId").getAsInt());
    }

    @Test
    void thePageHasItsOwnPermissionCopiedFromTheOldOne() throws Exception {
        String migration =
                Files.readString(Path.of("src/main/resources/db/migration/V20261031170000__emustats_permission.sql"));

        assertEquals("acc_emustats", EmuStatsHttpHandler.PERMISSION);
        assertTrue(migration.contains("('acc_emustats', 1,"));
        assertTrue(migration.contains("source.`permission_key` = ''acc_modtool_room_info''"));
        assertTrue(migration.contains("@emustats_existed > 0"));
        assertFalse(migration.toUpperCase().contains("DELETE"));
    }
}
