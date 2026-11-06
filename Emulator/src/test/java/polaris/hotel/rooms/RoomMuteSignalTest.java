package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import polaris.messages.outgoing.users.MutedWhisperComposer;

/**
 * AIR's IgnoreResult ({@code IgnoredUsersManager.onIgnoreResult}) reads 1 as "added", 2 as "added, oldest dropped
 * because the list was full" and 3 as "removed", all on the receiver's own ignore list. A mute is signalled to the
 * muted user alone with RemainingMutePeriod, never as an IgnoreResult to the room.
 */
class RoomMuteSignalTest {
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void ignoreResultCodesKeepAirMeaning() {
        assertEquals(1, RoomUserIgnoredComposer.IGNORED);
        assertEquals(2, RoomUserIgnoredComposer.IGNORED_LIST_FULL);
        assertEquals(3, RoomUserIgnoredComposer.UNIGNORED);

        ByteBuf packet = new RoomUserIgnoredComposer(42, RoomUserIgnoredComposer.IGNORED_LIST_FULL)
                .compose()
                .get();
        packet.skipBytes(6);
        assertEquals(2, packet.readInt());
        assertEquals(42, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void aRoomMuteTellsTheMutedUserTheRemainingPeriod() {
        Room room = new Room(41, 7);
        GameClient client = mock(GameClient.class);
        User user = user(42, client);

        room.muteUser(user, 2);

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client, times(1)).sendResponse(sent.capture());
        MutedWhisperComposer whisper = assertInstanceOf(MutedWhisperComposer.class, sent.getValue());
        assertTrue(whisper.getSeconds() > 60 && whisper.getSeconds() <= 120, "remaining seconds of a 2 minute mute");
        assertTrue(room.isMuted(user));

        room.unmuteUser(user);

        assertFalse(room.isMuted(user));
        verifyNoMoreInteractions(client);
    }

    @Test
    void noMutePathBroadcastsAnIgnoreResult() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("RoomUserIgnoredComposer.MUTED")
                        || source.contains("sendComposer(new RoomUserIgnoredComposer")) {
                    offenders.add(MAIN.relativize(file).toString());
                }
            }
        }

        assertEquals(List.of(), offenders, "An IgnoreResult only ever reports the receiver's own ignore list");
    }

    private static User user(int id, GameClient client) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(client);
        return user;
    }
}
