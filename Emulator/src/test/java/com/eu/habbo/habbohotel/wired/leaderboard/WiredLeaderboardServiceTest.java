package com.eu.habbo.habbohotel.wired.leaderboard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLeaderboard;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredLeaderboardChunkComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        when(box.getRankedVariables(any())).thenReturn(List.of(coins));
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
    void aUserVariableReferenceIsRankedWithTheStackedVariables() {
        WiredExtraVariableReference shared = mock(WiredExtraVariableReference.class);
        when(shared.getId()).thenReturn(901);
        when(shared.isUserReference()).thenReturn(true);
        when(shared.getVariableName()).thenReturn("shared");
        WiredExtraVariableReference global = mock(WiredExtraVariableReference.class);
        when(global.getId()).thenReturn(902);
        when(global.isRoomReference()).thenReturn(true);
        WiredExtraVariableReference sharedArray = mock(WiredExtraVariableReference.class);
        when(sharedArray.getId()).thenReturn(903);
        when(sharedArray.isUserReference()).thenReturn(true);
        when(sharedArray.isArrayDeclared()).thenReturn(true);
        Item item = mock(Item.class);
        WiredExtraVariableLeaderboard box = new WiredExtraVariableLeaderboard(5, 1, item, "", 0, 0);
        when(specialTypes.getExtras(0, 0))
                .thenReturn(new LinkedHashSet<>(List.of(shared, global, sharedArray, coins, box)));

        assertEquals(List.of(coins, shared), box.getRankedVariables(room));
        assertEquals("shared", WiredExtraVariableLeaderboard.nameOf(shared));
        assertFalse(WiredExtraVariableLeaderboard.isRankable(global), "room references have no holders to rank");

        // The rows are the store's holders of the reference, which reads the shared variable's saved rows.
        RoomUserVariableStore store = mock(RoomUserVariableStore.class);
        when(userVariables.getStore()).thenReturn(store);
        when(specialTypes.getExtra(901)).thenReturn(shared);
        when(store.count(901)).thenReturn(1);
        when(store.page(901, RoomUserVariableStore.Order.VALUE, true, 0, WiredLeaderboardChunk.SIZE))
                .thenReturn(List.of(new RoomUserVariableStore.Holder(5, "ann", 42, 100, 200)));
        when(store.figures(any())).thenReturn(Map.of());

        WiredLeaderboardChunk chunk = WiredLeaderboardQuery.chunk(room, WiredLeaderboardCodec.defaults(1, 1), "901", 0);
        assertEquals(1, chunk.totalCount());
        assertEquals("42", chunk.rows().get(0).rawValue());
        assertEquals(
                0,
                WiredLeaderboardQuery.chunk(room, WiredLeaderboardCodec.defaults(1, 1), "902", 0)
                        .totalCount());
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

    /** A filtered board with one display name. */
    private static WiredLeaderboardConfig filtered(String filter, String coins) {
        TreeMap<String, String> overrides = new TreeMap<>();
        overrides.put(coins, "Gold");
        return new WiredLeaderboardConfig(
                "Top",
                "",
                WiredLeaderboardCodec.SORT_HIGHEST_VALUE,
                0,
                List.of(WiredLeaderboardCodec.defaultColumn(WiredLeaderboardCodec.COLUMN_USERNAME)),
                true,
                false,
                WiredLeaderboardCodec.OPERAND_LITERAL,
                filter,
                WiredLeaderboardCodec.NULL_VARIABLE_ID,
                "",
                overrides,
                1,
                1,
                0,
                0);
    }

    /** What the box holds after loading a stored row with this config. */
    private static WiredLeaderboardConfig load(WiredLeaderboardConfig config) {
        WiredLeaderboardCodec.Decoded decoded =
                WiredLeaderboardCodec.decode(WiredLeaderboardCodec.encode(config), 1, 1);
        assertTrue(decoded.valid(), decoded.error());
        return decoded.config();
    }

    @Test
    void anOldRowAndAReSavedRowShowTheSamePanelAndRows() {
        WiredLeaderboardConfig oldRow = load(filtered("77", "900"));
        WiredLeaderboardConfig newRow = load(filtered("user:77", "user:900"));
        assertArrayEquals(
                WiredLeaderboardCodec.encode(newRow).variableIds(),
                WiredLeaderboardCodec.encode(oldRow).variableIds());
        assertEquals("user:77", oldRow.filterVariableId());
        assertEquals(newRow.displayOverrides(), oldRow.displayOverrides());

        // The panel offers the variable under its list id, with the stored display name of either row.
        WiredExtraVariableLeaderboard box = board(20);
        when(box.getConfig()).thenReturn(oldRow);
        WiredLeaderboardService service = new WiredLeaderboardService(1);
        service.flush(room);
        WiredLeaderboardDescriptor before = service.descriptors().get(0);
        assertEquals(List.of(new WiredLeaderboardDescriptor.Choice("user:900", "Gold")), before.choices());
        assertEquals("user:900", before.defaultVariableId());
        assertEquals("user:900", before.choiceId("900"));
        assertEquals("user:900", before.choiceId("user:900"));
        assertEquals(null, before.choiceId("room:900"));

        // The row saved again with the list ids keeps the panel and its revision: nothing it ranks changed.
        when(box.getConfig()).thenReturn(newRow);
        service.flush(room);
        assertEquals(before, service.descriptors().get(0));

        // Both rows read the same holders.
        RoomUserVariableStore store = mock(RoomUserVariableStore.class);
        when(userVariables.getStore()).thenReturn(store);
        when(specialTypes.getExtra(900)).thenReturn(coins);
        when(store.page(77, RoomUserVariableStore.Order.ID, false, 0, WiredLeaderboardQuery.MAX_FILTERED_HOLDERS))
                .thenReturn(List.of(new RoomUserVariableStore.Holder(5, "ann", 1, 100, 200)));
        when(store.page(900, RoomUserVariableStore.Order.VALUE, true, 0, WiredLeaderboardQuery.MAX_FILTERED_HOLDERS))
                .thenReturn(List.of(
                        new RoomUserVariableStore.Holder(5, "ann", 42, 100, 200),
                        new RoomUserVariableStore.Holder(6, "bob", 40, 100, 200)));
        when(store.figures(any())).thenReturn(Map.of());
        WiredLeaderboardChunk fromOld = WiredLeaderboardQuery.chunk(room, oldRow, "900", 0);
        WiredLeaderboardChunk fromNew = WiredLeaderboardQuery.chunk(room, newRow, "user:900", 0);
        assertEquals(1, fromOld.totalCount());
        assertEquals(fromOld.toString(), fromNew.toString());
    }

    @Test
    void aSubscriptionByABareIdIsTheListIdsVariableAndEchoesWhatWasSent() {
        WiredExtraVariableLeaderboard box = board(20);
        when(box.getConfig()).thenReturn(load(filtered("user:77", "user:900")));
        RoomUserVariableStore store = mock(RoomUserVariableStore.class);
        when(userVariables.getStore()).thenReturn(store);
        when(specialTypes.getExtra(900)).thenReturn(coins);
        when(store.figures(any())).thenReturn(Map.of());
        WiredLeaderboardService service = new WiredLeaderboardService(1);
        service.flush(room);
        int revision = service.descriptors().get(0).revision();

        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(5);
        when(habbo.getClient()).thenReturn(client);

        service.subscribe(room, habbo, 1, revision, "user:900", new int[] {0});
        service.subscribe(room, habbo, 1, revision, "900", new int[] {0});
        service.subscribe(room, habbo, 1, revision, "user:901", new int[] {0});

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client, atLeastOnce()).sendResponse(sent.capture());
        List<String> echoed = sent.getAllValues().stream()
                .filter(WiredLeaderboardChunkComposer.class::isInstance)
                .map(WiredLeaderboardServiceTest::chunkVariableId)
                .toList();
        assertEquals(List.of("user:900", "900"), echoed);
    }

    private static String chunkVariableId(MessageComposer composer) {
        ByteBuf body = composer.compose().get();
        try {
            body.skipBytes(6 + 8);
            byte[] text = new byte[body.readShort()];
            body.readBytes(text);
            return new String(text, StandardCharsets.UTF_8);
        } finally {
            body.release();
        }
    }
}
