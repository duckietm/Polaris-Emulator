package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.permissions.Permission;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * :summonrank moved every online user of a rank into the sender's room, past doors and bans: higher ranks,
 * unkickable staff and users in the middle of a trade included, and with rank 0 the whole hotel.
 */
class SummonRankCommandTargetsTest {

    private static User user(int id, int rankId, Room room) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        when(info.getCurrentRoom()).thenReturn(room);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    @Test
    void aLowerRankElsewhereIsBrought() {
        Room target = mock(Room.class);
        User sender = user(1, 7, target);

        assertTrue(SummonRankCommand.summons(sender, user(2, 3, mock(Room.class)), 2, target));
        assertTrue(SummonRankCommand.summons(sender, user(3, 3, null), 2, target), "a user in no room");
    }

    @Test
    void theSenderTheRoomAndTheRankAskedForAreRespected() {
        Room target = mock(Room.class);
        User sender = user(1, 7, target);

        assertFalse(SummonRankCommand.summons(sender, sender, 1, target));
        assertFalse(SummonRankCommand.summons(sender, user(2, 3, target), 1, target), "already there");
        assertFalse(
                SummonRankCommand.summons(sender, user(3, 3, mock(Room.class)), 5, target), "below the rank asked for");
    }

    @Test
    void unkickableUsersAndTradersStay() {
        Room target = mock(Room.class);
        User sender = user(1, 7, target);

        User unkickable = user(2, 3, mock(Room.class));
        when(unkickable.hasPermission(Permission.ACC_UNKICKABLE)).thenReturn(true);
        assertFalse(SummonRankCommand.summons(sender, unkickable, 1, target));

        Room tradeRoom = mock(Room.class);
        User trader = user(3, 3, tradeRoom);
        when(tradeRoom.getActiveTradeForUser(trader)).thenReturn(mock(RoomTrade.class));
        assertFalse(SummonRankCommand.summons(sender, trader, 1, target));
    }

    @Test
    void theRankAndTheNumberMovedAreBounded() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/commands/SummonRankCommand.java"));

        assertTrue(source.contains("minRank = Math.max(1, minRank);"));
        assertTrue(source.contains("target.getUsersMax() - target.getUserCount()"));
        assertTrue(source.contains("CommandTargetGuard.canTarget(sender, user)"), "a higher rank is never moved");
    }
}
