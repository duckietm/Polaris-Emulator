package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * :summonrank moved every online user of a rank into the sender's room, past doors and bans: higher ranks,
 * unkickable staff and users in the middle of a trade included, and with rank 0 the whole hotel.
 */
class SummonRankCommandTargetsTest {

    private static Habbo habbo(int id, int rankId, Room room) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        when(info.getCurrentRoom()).thenReturn(room);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    @Test
    void aLowerRankElsewhereIsBrought() {
        Room target = mock(Room.class);
        Habbo sender = habbo(1, 7, target);

        assertTrue(SummonRankCommand.summons(sender, habbo(2, 3, mock(Room.class)), 2, target));
        assertTrue(SummonRankCommand.summons(sender, habbo(3, 3, null), 2, target), "a user in no room");
    }

    @Test
    void theSenderTheRoomAndTheRankAskedForAreRespected() {
        Room target = mock(Room.class);
        Habbo sender = habbo(1, 7, target);

        assertFalse(SummonRankCommand.summons(sender, sender, 1, target));
        assertFalse(SummonRankCommand.summons(sender, habbo(2, 3, target), 1, target), "already there");
        assertFalse(SummonRankCommand.summons(sender, habbo(3, 3, mock(Room.class)), 5, target), "below the rank asked for");
    }

    @Test
    void unkickableUsersAndTradersStay() {
        Room target = mock(Room.class);
        Habbo sender = habbo(1, 7, target);

        Habbo unkickable = habbo(2, 3, mock(Room.class));
        when(unkickable.hasPermission(Permission.ACC_UNKICKABLE)).thenReturn(true);
        assertFalse(SummonRankCommand.summons(sender, unkickable, 1, target));

        Room tradeRoom = mock(Room.class);
        Habbo trader = habbo(3, 3, tradeRoom);
        when(tradeRoom.getActiveTradeForHabbo(trader)).thenReturn(mock(RoomTrade.class));
        assertFalse(SummonRankCommand.summons(sender, trader, 1, target));
    }

    @Test
    void theRankAndTheNumberMovedAreBounded() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/SummonRankCommand.java"));

        assertTrue(source.contains("minRank = Math.max(1, minRank);"));
        assertTrue(source.contains("target.getUsersMax() - target.getUserCount()"));
        assertTrue(source.contains("CommandTargetGuard.canTarget(sender, habbo)"), "a higher rank is never moved");
    }
}
