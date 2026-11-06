package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.ServerMessage;
import polaris.threading.ThreadPooling;

/**
 * :reload ran for anyone the permission's "with rights" level let through, group members included: they
 * could unload and reload the room in a loop, sending everyone back in and resetting games and wired.
 * A failed reload also threw on the null room.
 */
class ReloadRoomOwnerOnlyTest {
    private MockedStatic<Emulator> emulator;
    private final ThreadPooling threading = mock(ThreadPooling.class);
    private final RoomManager rooms = mock(RoomManager.class);
    private final User issuer = mock(User.class);
    private final UserInfo info = mock(UserInfo.class);
    private final GameClient client = mock(GameClient.class);

    @BeforeEach
    void setUp() {
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> i.getArgument(0));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(this.rooms);
        when(this.threading.run(any(Runnable.class), anyLong())).thenAnswer(i -> {
            i.<Runnable>getArgument(0).run();
            return null;
        });
        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getTexts).thenReturn(texts);
        this.emulator.when(Emulator::getThreading).thenReturn(this.threading);
        this.emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

        when(this.issuer.getUserInfo()).thenReturn(this.info);
        when(this.client.getUser()).thenReturn(this.issuer);
    }

    @AfterEach
    void tearDown() {
        this.emulator.close();
    }

    @Test
    void aRoomIsReloadedAtMostOncePerCooldown() {
        int room = 912_345;
        assertTrue(ReloadRoomCommand.claimReload(room, 1_000_000L));
        assertFalse(ReloadRoomCommand.claimReload(room, 1_000_000L + ReloadRoomCommand.COOLDOWN_MILLIS - 1));
        assertTrue(ReloadRoomCommand.claimReload(room, 1_000_000L + ReloadRoomCommand.COOLDOWN_MILLIS));
        assertTrue(ReloadRoomCommand.claimReload(room + 1, 1_000_000L), "another room has its own cooldown");
    }

    @Test
    void someoneWithRightsCannotReloadTheRoom() throws Exception {
        Room room = this.room(912_400);
        when(room.isOwner(this.issuer)).thenReturn(false);

        new ReloadRoomCommand().handle(this.client, new String[] {"reload"});

        verify(this.threading, never()).run(any(Runnable.class), anyLong());
        verify(this.rooms, never()).unloadRoom(any());
        verify(this.issuer).whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
    }

    @Test
    void withoutARoomNothingThrowsAndNothingIsReloaded() throws Exception {
        when(this.info.getCurrentRoom()).thenReturn(null);

        new ReloadRoomCommand().handle(this.client, new String[] {"reload"});

        verify(this.rooms, never()).unloadRoom(any());
        verify(this.issuer).whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
    }

    @Test
    void theOwnerReloadsAndEveryoneIsSentToTheRoomId() throws Exception {
        Room room = this.room(912_401);
        when(room.isOwner(this.issuer)).thenReturn(true);
        User visitor = mock(User.class);
        GameClient visitorClient = mock(GameClient.class);
        when(visitor.getClient()).thenReturn(visitorClient);
        when(room.getUsers()).thenReturn(List.of(visitor));

        new ReloadRoomCommand().handle(this.client, new String[] {"reload"});

        verify(this.rooms).unloadRoom(room);
        verify(this.rooms).loadRoom(912_401);
        verify(visitorClient).sendResponse(any(ServerMessage.class));
    }

    private Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(this.info.getCurrentRoom()).thenReturn(room);
        return room;
    }
}
