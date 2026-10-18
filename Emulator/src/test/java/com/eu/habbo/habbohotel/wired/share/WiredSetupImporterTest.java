package com.eu.habbo.habbohotel.wired.share;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** An import end to end on mocks: matching, reference remapping and the boxes' own save path. */
class WiredSetupImporterTest {
    private static final int IMPORTER = 7;

    private Room room;
    private GameClient client;
    private InteractionWiredTrigger trigger;
    private InteractionWiredEffect action;
    private InteractionWiredEffect strangersAction;
    private HabboItem chair;

    private static Item base(String name) {
        Item item = mock(Item.class);
        when(item.getName()).thenReturn(name);
        when(item.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }

    private static <T extends HabboItem> T place(T item, int id, String type, int x, int y, int owner) {
        when(item.getId()).thenReturn(id);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getUserId()).thenReturn(owner);
        Item base = base(type);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    @BeforeEach
    void setUp() throws Exception {
        this.room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(this.room.getLayout()).thenReturn(layout);
        when(layout.getTile(anyShort(), anyShort()))
                .thenAnswer(call ->
                        new RoomTile(call.getArgument(0), call.getArgument(1), (short) 0, RoomTileState.OPEN, true));

        this.trigger = place(mock(InteractionWiredTrigger.class), 100, "wf_trg_walks_on_furni", 10, 10, IMPORTER);
        this.action = place(mock(InteractionWiredEffect.class), 200, "wf_act_toggle_state", 11, 10, IMPORTER);
        this.strangersAction = place(mock(InteractionWiredEffect.class), 300, "wf_act_toggle_state", 11, 11, 99);
        this.chair = place(mock(HabboItem.class), 400, "chair_plasto", 12, 10, 99);
        when(this.trigger.saveData(any(WiredSettings.class), any())).thenReturn(true);
        when(this.action.saveData(any(WiredSettings.class), any())).thenReturn(true);

        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(this.room.getRoomSpecialTypes()).thenReturn(types);
        when(types.getTriggers()).thenReturn(Set.of(this.trigger));
        when(types.getEffects()).thenReturn(Set.of(this.action, this.strangersAction));
        when(types.getConditions()).thenReturn(Set.of());
        when(types.getExtras()).thenReturn(Set.of());
        when(this.room.getItemsAt(anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0);
            int y = call.getArgument(1);
            return x == 12 && y == 10 ? Set.of(this.chair) : Set.of();
        });

        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(IMPORTER);
        when(habbo.getHabboInfo()).thenReturn(info);
        this.client = mock(GameClient.class);
        when(this.client.getHabbo()).thenReturn(habbo);
    }

    /** A trigger at the anchor, an action next to it picking the trigger and a chair, and an action on a stranger's tile. */
    private static WiredSetupDocument setup() {
        WiredSetupDocument document = new WiredSetupDocument();
        document.anchorX = 10;
        document.anchorY = 10;

        WiredSetupDocument.Box trigger = new WiredSetupDocument.Box();
        trigger.type = "wf_trg_walks_on_furni";
        trigger.holder = "TRIGGER";
        trigger.picks = List.of("f0");
        document.boxes.add(trigger);

        WiredSetupDocument.Box action = new WiredSetupDocument.Box();
        action.type = "wf_act_toggle_state";
        action.holder = "ACTION";
        action.dx = 1;
        action.ints = new int[] {4};
        action.text = "custom:#0;custom:#2";
        action.picks = List.of("b0", "f0", "f9");
        action.delay = 3;
        document.boxes.add(action);

        WiredSetupDocument.Box onStrangersBox = new WiredSetupDocument.Box();
        onStrangersBox.type = "wf_act_toggle_state";
        onStrangersBox.holder = "ACTION";
        onStrangersBox.dx = 1;
        onStrangersBox.dy = 1;
        document.boxes.add(onStrangersBox);

        WiredSetupDocument.Furni chair = new WiredSetupDocument.Furni();
        chair.type = "chair_plasto";
        chair.dx = 2;
        document.furni.add(chair);
        return document;
    }

    @Test
    void aPreviewMatchesTheImportersOwnBoxesAndSavesNothing() throws Exception {
        WiredSetupImporter.Report report = new WiredSetupImporter(this.room, this.client, setup())
                .run(WiredSetupShare.ANCHOR_ORIGINAL, false, false);

        assertEquals(WiredSetupShare.STATUS_OK, report.status);
        assertEquals(WiredSetupImporter.ENTRY_CONFIGURE, report.entries.get(0).status);
        assertEquals(100, report.entries.get(0).itemId);
        assertEquals(WiredSetupImporter.ENTRY_CONFIGURE, report.entries.get(1).status);
        // Another user's box on the target tile is never touched.
        assertEquals(WiredSetupImporter.ENTRY_MISSING, report.entries.get(2).status);
        assertEquals(2, report.configured);
        assertEquals(1, report.missing);
        verify(this.trigger, never()).saveData(any(WiredSettings.class), any());
        verify(this.strangersAction, never()).saveData(any(WiredSettings.class), any());
    }

    @Test
    void aConfirmSavesEachBoxWithRemappedReferences() throws Exception {
        WiredSetupImporter.Report report = new WiredSetupImporter(this.room, this.client, setup())
                .run(WiredSetupShare.ANCHOR_ORIGINAL, false, true);

        assertTrue(report.confirmed);
        ArgumentCaptor<WiredSettings> triggerSettings = ArgumentCaptor.forClass(WiredSettings.class);
        verify(this.trigger).saveData(triggerSettings.capture(), any());
        assertArrayEquals(new int[] {400}, triggerSettings.getValue().getFurniIds());

        ArgumentCaptor<WiredSettings> actionSettings = ArgumentCaptor.forClass(WiredSettings.class);
        verify(this.action).saveData(actionSettings.capture(), any());
        WiredSettings saved = actionSettings.getValue();
        assertArrayEquals(new int[] {4}, saved.getIntParams());
        // Box 2 matched nothing, so its variable reference is dropped to an id no box has.
        assertEquals("custom:100;custom:0", saved.getStringParam());
        assertArrayEquals(new int[] {100, 400}, saved.getFurniIds());
        assertEquals(3, saved.getDelay());
        verify(this.action).markEditorSaved();
        verify(this.strangersAction, never()).saveData(any(WiredSettings.class), any());
    }

    @Test
    void theAvatarAnchorMovesTheWholeSetup() throws Exception {
        Habbo habbo = this.client.getHabbo();
        com.eu.habbo.habbohotel.rooms.RoomUnit unit = mock(com.eu.habbo.habbohotel.rooms.RoomUnit.class);
        when(habbo.getRoomUnit()).thenReturn(unit);
        when(unit.getCurrentLocation())
                .thenReturn(new RoomTile((short) 20, (short) 3, (short) 0, RoomTileState.OPEN, true));

        WiredSetupImporter.Report report = new WiredSetupImporter(this.room, this.client, setup())
                .run(WiredSetupShare.ANCHOR_AVATAR, false, false);

        assertEquals(20, report.anchorX);
        assertEquals(21, report.entries.get(1).x);
        assertEquals(3, report.entries.get(1).y);
        assertEquals(WiredSetupImporter.ENTRY_MISSING, report.entries.get(0).status);
    }

    @Test
    void anExportReadsBackIntoTheSameReferences() {
        when(this.room.getHabboItem(400)).thenReturn(this.chair);
        WiredSetupExporter exporter = new WiredSetupExporter(this.room);
        exporter.boxIndexes.put(100, 0);
        exporter.build(List.of());
        // build() resets the anchor; the picks keep their shape.
        exporter.boxIndexes.put(100, 0);
        assertEquals(List.of("b0", "f0"), exporter.exportPicks(new int[] {100, 400, 999}));
    }

    @Test
    void aTooLargeReportStillFitsOnePacket() {
        WiredSetupImporter.Report report = new WiredSetupImporter.Report();
        for (int i = 0; i < 300; i++) {
            WiredSetupImporter.Entry entry = new WiredSetupImporter.Entry();
            entry.type = "x".repeat(100);
            entry.reason = "y".repeat(200);
            report.entries.add(entry);
        }
        String json = WiredSetupShare.reportJson(report);
        assertTrue(json.length() <= WiredSetupShare.MAX_REPORT_BYTES);
        assertTrue(json.contains("\"entries\""));
    }
}
