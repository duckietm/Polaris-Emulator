package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The room settings window saves who may kick, mute and ban, but the handlers only asked whether the
 * user held rights: "owner only" never applied, and "everyone may kick" never applied either.
 */
class RoomModerationOptionsTest {

    private static final int OWNER = 1;
    private static final int RIGHTS = 2;
    private static final int VISITOR = 3;

    private static Habbo habbo(int id) {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private static RoomRightsManager rights(int mute, int kick, int ban) {
        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(OWNER);
        when(room.getMuteOption()).thenReturn(mute);
        when(room.getKickOption()).thenReturn(kick);
        when(room.getBanOption()).thenReturn(ban);
        return new RoomRightsManager(
                room, IntArrayList.of(RIGHTS), new Int2ObjectOpenHashMap<>(), new Int2IntOpenHashMap());
    }

    @Test
    void ownerOnlyKeepsRightsHoldersOut() {
        RoomRightsManager manager = rights(0, 0, 0);

        assertTrue(manager.canKick(habbo(OWNER)));
        assertTrue(manager.canMute(habbo(OWNER)));
        assertTrue(manager.canBan(habbo(OWNER)));
        assertFalse(manager.canKick(habbo(RIGHTS)));
        assertFalse(manager.canMute(habbo(RIGHTS)));
        assertFalse(manager.canBan(habbo(RIGHTS)));
    }

    @Test
    void rightsHoldersMayWhenTheRoomSaysSo() {
        RoomRightsManager manager = rights(1, 1, 1);

        assertTrue(manager.canKick(habbo(RIGHTS)));
        assertTrue(manager.canMute(habbo(RIGHTS)));
        assertTrue(manager.canBan(habbo(RIGHTS)));
        assertFalse(manager.canKick(habbo(VISITOR)));
    }

    @Test
    void everyoneMayKickButNotMuteOrBan() {
        RoomRightsManager manager = rights(2, 2, 2);

        assertTrue(manager.canKick(habbo(VISITOR)));
        assertFalse(manager.canMute(habbo(VISITOR)), "mute has no everyone level");
        assertFalse(manager.canBan(habbo(VISITOR)), "ban has no everyone level");
        assertTrue(manager.canMute(habbo(RIGHTS)), "a stored 2 still lets rights holders mute");
    }

    @Test
    void staffWhoOwnAnyRoomAlwaysMay() {
        Habbo staff = habbo(VISITOR);
        when(staff.hasPermission(Permission.ACC_ANYROOMOWNER)).thenReturn(true);

        assertTrue(rights(0, 0, 0).canBan(staff));
    }

    @Test
    void theHandlersAskTheRoomSetting() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo");

        assertTrue(Files.readString(base.resolve("messages/incoming/rooms/users/RoomUserKickEvent.java"))
                .contains("room.getRightsManager().canKick(this.client.getHabbo())"));
        assertTrue(Files.readString(base.resolve("messages/incoming/rooms/users/RoomUserMuteEvent.java"))
                .contains("room.getRightsManager().canMute(this.client.getHabbo())"));
        assertTrue(Files.readString(base.resolve("habbohotel/rooms/RoomModerationService.java"))
                .contains("room.getRightsManager().canBan(rights)"));
    }
}
