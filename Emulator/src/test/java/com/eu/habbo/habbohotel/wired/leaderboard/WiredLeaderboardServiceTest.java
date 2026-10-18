package com.eu.habbo.habbohotel.wired.leaderboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLeaderboard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Panel numbering, the room's revision counter, the push grid and the row times. */
class WiredLeaderboardServiceTest {
    private final Room room = mock(Room.class);
    private final RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
    private final RoomUserVariableManager userVariables = mock(RoomUserVariableManager.class);
    private final Set<InteractionWiredExtra> extras = new LinkedHashSet<>();
    private final WiredExtraUserVariable coins = mock(WiredExtraUserVariable.class);

    WiredLeaderboardServiceTest() {
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtras()).thenReturn(extras);
        when(room.getUserVariableManager()).thenReturn(userVariables);
        when(coins.getId()).thenReturn(900);
        when(coins.getVariableName()).thenReturn("coins");
    }

    private WiredExtraVariableLeaderboard board(int id) {
        WiredExtraVariableLeaderboard box = mock(WiredExtraVariableLeaderboard.class);
        when(box.getId()).thenReturn(id);
        when(box.isPublished()).thenReturn(true);
        when(box.getConfig()).thenReturn(WiredLeaderboardCodec.defaults(1, 1));
        when(box.getCandidates(any())).thenReturn(List.of(coins));
        this.extras.add(box);
        return box;
    }

    @Test
    void panelsAreNumberedFromOneWithOneRevisionCounterPerRoom() {
        WiredExtraVariableLeaderboard later = board(70);
        WiredExtraVariableLeaderboard earlier = board(20);
        WiredLeaderboardService service = new WiredLeaderboardService(1);

        service.flush(room);
        List<WiredLeaderboardDescriptor> first = service.descriptors();
        assertEquals(
                List.of(1, 2),
                first.stream().map(WiredLeaderboardDescriptor::overlayId).toList());
        assertEquals(
                List.of(1, 2),
                first.stream().map(WiredLeaderboardDescriptor::revision).toList());
        assertEquals(1, WiredLeaderboardService.overlayIdOf(room, earlier));
        assertEquals(2, WiredLeaderboardService.overlayIdOf(room, later));

        // The first board goes: the second now shows as panel 1, with a revision newer than any panel had.
        this.extras.remove(earlier);
        service.flush(room);
        List<WiredLeaderboardDescriptor> second = service.descriptors();
        assertEquals(1, second.size());
        assertEquals(1, second.get(0).overlayId());
        assertEquals(3, second.get(0).revision());
    }

    @Test
    void rowUpdatesGoOutOnATenSecondGrid() {
        assertEquals(WiredLeaderboardService.pushSlot(10_300), WiredLeaderboardService.pushSlot(19_999));
        assertTrue(WiredLeaderboardService.pushSlot(20_000) > WiredLeaderboardService.pushSlot(19_999));
    }

    @Test
    void rowTimesAreSentOnlyForAShownTimeColumn() {
        RoomUserVariableStore store = mock(RoomUserVariableStore.class);
        when(userVariables.getStore()).thenReturn(store);
        when(store.figures(any())).thenReturn(Map.of());
        List<RoomUserVariableStore.Holder> holders = List.of(new RoomUserVariableStore.Holder(5, "ann", 3, 100, 200));

        WiredLeaderboardRow plain = WiredLeaderboardQuery.rows(
                        room, WiredLeaderboardCodec.defaults(1, 1), coins, holders, 0)
                .get(0);
        assertEquals(0L, plain.creationTime());
        assertEquals(0L, plain.lastUpdateTime());

        WiredLeaderboardConfig timed = new WiredLeaderboardConfig(
                "",
                "",
                WiredLeaderboardCodec.SORT_HIGHEST_VALUE,
                0,
                List.of(
                        WiredLeaderboardCodec.defaultColumn(WiredLeaderboardCodec.COLUMN_USERNAME),
                        WiredLeaderboardCodec.defaultColumn(WiredLeaderboardCodec.COLUMN_LAST_UPDATE),
                        WiredLeaderboardCodec.defaultColumn(WiredLeaderboardCodec.COLUMN_CREATION)),
                false,
                false,
                WiredLeaderboardCodec.OPERAND_LITERAL,
                WiredLeaderboardCodec.NULL_VARIABLE_ID,
                WiredLeaderboardCodec.NULL_VARIABLE_ID,
                "",
                new TreeMap<>(),
                1,
                1,
                0,
                0);
        WiredLeaderboardRow row =
                WiredLeaderboardQuery.rows(room, timed, coins, holders, 0).get(0);
        assertEquals(100_000L, row.creationTime());
        assertEquals(200_000L, row.lastUpdateTime());
    }
}
