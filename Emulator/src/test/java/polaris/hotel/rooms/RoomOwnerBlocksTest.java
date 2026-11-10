package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.RoomEnterErrorComposer;

/** "Blocked by the room owner": who is refused at the door, and what the refusal does. */
class RoomOwnerBlocksTest {
    private static final int OWNER = 7;
    private static final int VISITOR = 9;

    private final DataSource dataSource = mock(DataSource.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet stored = mock(ResultSet.class);
    private final Room room = mock(Room.class);
    private User onlineOwner;

    @BeforeEach
    void setUp() throws Exception {
        Connection connection = mock(Connection.class);
        when(this.dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(this.statement);
        when(this.statement.executeQuery()).thenReturn(this.stored);
        when(this.room.getOwnerId()).thenReturn(OWNER);
        when(this.room.getId()).thenReturn(41);
    }

    private RoomOwnerBlocks blocks() {
        return new RoomOwnerBlocks(id -> id == OWNER ? this.onlineOwner : null, () -> this.dataSource);
    }

    private static User user(int id) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRoomQueueId()).thenReturn(41);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        return user;
    }

    private void ownerOnline(boolean blockedVisitor) {
        this.onlineOwner = user(OWNER);
        UserStats stats = mock(UserStats.class);
        when(stats.userBlocked(VISITOR)).thenReturn(blockedVisitor);
        when(this.onlineOwner.getUserStats()).thenReturn(stats);
    }

    @Test
    void anOnlineOwnersListDecidesWithoutTheDatabase() throws Exception {
        ownerOnline(true);
        assertTrue(blocks().refusesEntry(user(VISITOR), this.room));

        ownerOnline(false);
        assertFalse(blocks().refusesEntry(user(VISITOR), this.room));
        verify(this.dataSource, never()).getConnection();
    }

    @Test
    void anOfflineOwnersStoredListDecides() throws Exception {
        when(this.stored.next()).thenReturn(true);
        assertTrue(blocks().refusesEntry(user(VISITOR), this.room));
        verify(this.statement).setInt(1, OWNER);
        verify(this.statement).setInt(2, VISITOR);

        when(this.stored.next()).thenReturn(false);
        assertFalse(blocks().refusesEntry(user(VISITOR), this.room));
    }

    @Test
    void theOwnerStaffAndOwnerlessRoomsAreNeverRefused() throws Exception {
        when(this.stored.next()).thenReturn(true);

        assertFalse(blocks().refusesEntry(user(OWNER), this.room), "the owner's own room");

        User staff = user(VISITOR);
        when(staff.hasPermission(Permission.ACC_ENTERANYROOM)).thenReturn(true);
        assertFalse(blocks().refusesEntry(staff, this.room), "staff who may enter any room");

        when(this.room.getOwnerId()).thenReturn(0);
        assertFalse(blocks().refusesEntry(user(VISITOR), this.room), "a room without an owner");
        verify(this.dataSource, never()).getConnection();
    }

    @Test
    void aFailedLookupLetsTheVisitorIn() throws Exception {
        when(this.dataSource.getConnection()).thenThrow(new SQLException("down"));

        assertFalse(blocks().refusesEntry(user(VISITOR), this.room));
    }

    @Test
    void aBlockedVisitorGetsTheBlockedErrorAndTheRoomStaysClosed() {
        List<String> opened = new ArrayList<>();
        when(this.room.getState()).thenReturn(RoomState.OPEN);
        ownerOnline(true);
        User visitor = user(VISITOR);

        new RoomEntryService(
                        id -> this.room,
                        ignored -> {},
                        ignored -> opened.add("content"),
                        ignored -> this.room,
                        (user, ignored) -> true,
                        (user, target, door, reconnect) -> opened.add("open"),
                        blocks()::refusesEntry)
                .enter(visitor, 41, "", false, null, false);

        assertEquals(List.of(), opened);
        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(visitor.getClient()).sendResponse(sent.capture());
        assertTrue(sent.getValue() instanceof RoomEnterErrorComposer);
        assertEquals(5, RoomEnterErrorComposer.ROOM_ERROR_BLOCKED);
    }

    @Test
    void aVisitorWhoIsNotBlockedEnters() {
        List<String> opened = new ArrayList<>();
        when(this.room.getState()).thenReturn(RoomState.OPEN);
        ownerOnline(false);

        new RoomEntryService(
                        id -> this.room,
                        ignored -> {},
                        ignored -> opened.add("content"),
                        ignored -> this.room,
                        (user, ignored) -> true,
                        (user, target, door, reconnect) -> opened.add("open"),
                        blocks()::refusesEntry)
                .enter(user(VISITOR), 41, "", false, null, false);

        assertEquals(List.of("content", "open"), opened);
    }

    @Test
    void aBlockShowsThePlayerOutOfTheBlockersRoom() {
        User owner = user(OWNER);
        User visitor = user(VISITOR);
        when(visitor.getUserInfo().getCurrentRoom()).thenReturn(this.room);

        assertTrue(RoomOwnerBlocks.showOut(owner, visitor));
        verify(this.room).kickUser(visitor, true);
    }

    @Test
    void aBlockLeavesPlayersElsewhereAndExemptPlayersAlone() {
        User owner = user(OWNER);

        User elsewhere = user(VISITOR);
        Room other = mock(Room.class);
        when(other.getOwnerId()).thenReturn(99);
        when(elsewhere.getUserInfo().getCurrentRoom()).thenReturn(other);
        assertFalse(RoomOwnerBlocks.showOut(owner, elsewhere), "in someone else's room");
        verify(other, never()).kickUser(any(), org.mockito.ArgumentMatchers.anyBoolean());

        User inNoRoom = user(VISITOR);
        assertFalse(RoomOwnerBlocks.showOut(owner, inNoRoom), "in no room");

        User unkickable = user(VISITOR);
        when(unkickable.getUserInfo().getCurrentRoom()).thenReturn(this.room);
        when(unkickable.hasPermission(Permission.ACC_UNKICKABLE)).thenReturn(true);
        assertFalse(RoomOwnerBlocks.showOut(owner, unkickable), "cannot be kicked");

        assertFalse(RoomOwnerBlocks.showOut(owner, null), "offline");
        verify(this.room, never()).kickUser(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void onlyTheOwnersBlockEmptiesARoom() {
        assertTrue(RoomOwnerBlocks.leavesOnBlock(7, 7, false));
        assertFalse(RoomOwnerBlocks.leavesOnBlock(7, 8, false));
        assertFalse(RoomOwnerBlocks.leavesOnBlock(7, 7, true));
        assertFalse(RoomOwnerBlocks.leavesOnBlock(0, 0, false));
    }
}
