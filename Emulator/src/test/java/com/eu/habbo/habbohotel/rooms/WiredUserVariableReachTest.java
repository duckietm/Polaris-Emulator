package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.wired.WiredVariableClearPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Assigning a user variable needed the user in the room; changing or removing one did not, so anyone
 * with wired rights could rewrite the stored values of any user id, offline included.
 */
class WiredUserVariableReachTest {

    private static Habbo habbo(int id) {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    @Test
    void aUserNotInTheRoomIsTheDefinitionOwnersToManage() {
        Room room = mock(Room.class);
        Habbo editor = habbo(5);

        try (MockedStatic<UserVariableHolders> holders = mockStatic(UserVariableHolders.class);
                MockedStatic<WiredVariableClearPolicy> policy = mockStatic(WiredVariableClearPolicy.class)) {
            holders.when(() -> UserVariableHolders.isInRoom(room, 40)).thenReturn(true);
            holders.when(() -> UserVariableHolders.isInRoom(room, 41)).thenReturn(false);
            policy.when(() -> WiredVariableClearPolicy.canClear(room, editor, 900))
                    .thenReturn(false);
            policy.when(() -> WiredVariableClearPolicy.canClear(room, editor, 901))
                    .thenReturn(true);

            assertTrue(RoomWiredVariableWrites.mayManageUser(room, editor, 40, 900), "a user in the room");
            assertFalse(RoomWiredVariableWrites.mayManageUser(room, editor, 41, 900), "an absent user, not the owner");
            assertTrue(RoomWiredVariableWrites.mayManageUser(room, editor, 41, 901), "an absent user, the owner");
        }
    }

    @Test
    void bothCreatorPacketsAskBeforeChangingAUser() throws Exception {
        for (String handler : new String[] {"WiredUserVariableUpdateEvent.java", "WiredUserVariableManageEvent.java"}) {
            String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/wired/" + handler))
                    .replaceAll("\\s+", "");

            assertTrue(
                    source.contains(
                            "RoomWiredVariableWrites.mayManageUser(room,this.client.getHabbo(),targetId,definitionItemId)"),
                    handler);
        }
    }
}
