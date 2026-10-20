package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Not In Group reads its quantifier as AIR's {@code quantifier.users.neg} texts: 0 "at least one of the users does not
 * match", 1 "none of the users match". An empty selection keeps the WiredQuantifier rule (0 fails, 1 passes).
 */
class WiredConditionNotInGroupQuantifierTest {
    private static final int GROUP = 9;

    private static RoomUnit user(Room room, int id, boolean member) {
        RoomUnit unit = WiredConditionTestSupport.user(room, id, 1);
        HabboStats stats = mock(HabboStats.class);
        when(stats.hasGuild(GROUP)).thenReturn(member);
        when(room.getHabbo(unit).getHabboStats()).thenReturn(stats);
        return unit;
    }

    private static boolean evaluate(int quantifier, Room room, List<RoomUnit> users) throws Exception {
        WiredConditionNotInGroup box =
                new WiredConditionNotInGroup(4, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        // The users of the selectors (200), the room's own group.
        when(set.getString("wired_data"))
                .thenReturn(
                        "{\"userSource\":200,\"groupType\":0,\"selectedGroupId\":0,\"quantifier\":" + quantifier + "}");
        box.loadWiredData(set, room);
        WiredContext ctx = WiredConditionTestSupport.context(room, null);
        ctx.targets().setUsers(users);
        return box.evaluate(ctx);
    }

    @Test
    void allReadsAsAtLeastOneUserIsNotAMember() throws Exception {
        Room room = WiredConditionTestSupport.room(2);
        when(room.getGuildId()).thenReturn(GROUP);
        RoomUnit member = user(room, 1, true);
        RoomUnit outsider = user(room, 2, false);

        assertTrue(evaluate(0, room, List.of(member, outsider)));
        assertFalse(evaluate(0, room, List.of(member)));
        assertTrue(evaluate(0, room, List.of(outsider)));
    }

    @Test
    void anyReadsAsNoneOfTheUsersIsAMember() throws Exception {
        Room room = WiredConditionTestSupport.room(2);
        when(room.getGuildId()).thenReturn(GROUP);
        RoomUnit member = user(room, 1, true);
        RoomUnit outsider = user(room, 2, false);

        assertFalse(evaluate(1, room, List.of(member, outsider)));
        assertTrue(evaluate(1, room, List.of(outsider)));
        assertFalse(evaluate(1, room, List.of(member)));
    }

    @Test
    void anEmptySelectionKeepsTheQuantifierRule() throws Exception {
        Room room = WiredConditionTestSupport.room(2);
        when(room.getGuildId()).thenReturn(GROUP);

        assertFalse(evaluate(0, room, List.of()));
        assertTrue(evaluate(1, room, List.of()));
    }
}
