package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import static com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTestSupport.body;
import static com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTestSupport.boxBase;
import static com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTestSupport.room;
import static com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTestSupport.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * "Furni state is changed" answers every state update of the furni it watches, wired-caused ones
 * included, while the user-toggle box keeps ignoring what effects do.
 */
class WiredTriggerFurniStateUpdatedTest {

    private static HabboItem furni(int id, String state) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getExtradata()).thenReturn(state);
        when(item.getRoomId()).thenReturn(81);
        return item;
    }

    private static Room roomWith(HabboItem item) {
        Room room = room(81);
        when(room.getHabboItem(item.getId())).thenReturn(item);
        return room;
    }

    private static WiredEvent stateEvent(WiredEvent.Type type, Room room, HabboItem item, boolean byEffect) {
        return WiredEvent.builder(type, room)
                .sourceItem(item)
                .triggeredByEffect(byEffect)
                .build();
    }

    @Test
    void firesOnAWiredCausedChangeOfAPickedFurniWhereTheToggleBoxStaysQuiet() throws Exception {
        HabboItem item = furni(500, "1");
        Room room = roomWith(item);
        String stored = "{\"triggerMode\":0,\"furniSource\":100,\"snapshots\":[{\"itemId\":500,\"state\":\"0\"}]}";

        WiredTriggerFurniStateUpdated updated = new WiredTriggerFurniStateUpdated(1, 1, boxBase(), "", 0, 0);
        updated.loadWiredData(row(stored), room);
        WiredTriggerFurniStateToggled toggled = new WiredTriggerFurniStateToggled(2, 1, boxBase(), "", 0, 0);
        toggled.loadWiredData(row(stored), room);

        WiredEvent byEffect = stateEvent(WiredEvent.Type.FURNI_STATE_UPDATED, room, item, true);
        WiredEvent byUser = stateEvent(WiredEvent.Type.FURNI_STATE_CHANGED, room, item, false);

        assertTrue(updated.matches(updated, byEffect));
        assertTrue(updated.matches(updated, byUser));
        assertFalse(toggled.matches(toggled, byEffect));
        assertTrue(toggled.matches(toggled, byUser));
    }

    @Test
    void ignoresFurniItDoesNotWatchAndHonoursTheSavedStateMode() throws Exception {
        HabboItem watched = furni(500, "1");
        HabboItem other = furni(501, "1");
        Room room = roomWith(watched);
        when(room.getHabboItem(501)).thenReturn(other);

        WiredTriggerFurniStateUpdated savedState = new WiredTriggerFurniStateUpdated(1, 1, boxBase(), "", 0, 0);
        savedState.loadWiredData(
                row("{\"triggerMode\":1,\"furniSource\":100,\"snapshots\":[{\"itemId\":500,\"state\":\"0\"}]}"), room);

        assertFalse(savedState.matches(savedState, stateEvent(WiredEvent.Type.FURNI_STATE_UPDATED, room, other, true)));
        assertFalse(
                savedState.matches(savedState, stateEvent(WiredEvent.Type.FURNI_STATE_UPDATED, room, watched, true)),
                "state 1 is not the saved state 0");

        when(watched.getExtradata()).thenReturn("0");

        assertTrue(
                savedState.matches(savedState, stateEvent(WiredEvent.Type.FURNI_STATE_UPDATED, room, watched, true)));
    }

    @Test
    void applyingTheSnapshotSetsOnlyTheSavedStates() throws Exception {
        HabboItem item = furni(500, "1");
        Room room = roomWith(item);
        WiredTriggerFurniStateUpdated box = new WiredTriggerFurniStateUpdated(1, 1, boxBase(), "", 0, 0);
        box.loadWiredData(
                row("{\"triggerMode\":0,\"furniSource\":100,\"snapshots\":[{\"itemId\":500,\"state\":\"0\"}]}"), room);

        List<WiredMatchFurniSetting> settings = new ArrayList<>(box.getMatchFurniSettings());

        assertEquals(1, settings.size());
        assertEquals(500, settings.get(0).item_id);
        assertEquals("0", settings.get(0).state);
        assertTrue(box.shouldMatchState());
        assertFalse(box.shouldMatchPosition() || box.shouldMatchRotation() || box.shouldMatchAltitude());
    }

    @Test
    void serializesUnderItsOwnCodeWithTheSharedDialogParams() throws Exception {
        WiredTriggerFurniStateUpdated box = new WiredTriggerFurniStateUpdated(7, 1, boxBase(), "", 0, 0);
        // Serializing drops picks that left the box's room, so the furni has to share its room id.
        HabboItem item = furni(500, "1");
        when(item.getRoomId()).thenReturn(box.getRoomId());
        Room room = roomWith(item);
        box.loadWiredData(
                row("{\"triggerMode\":1,\"furniSource\":100,\"snapshots\":[{\"itemId\":500,\"state\":\"0\"}]}"), room);

        WiredTriggerTestSupport.Body body = body(box, room);

        assertEquals(WiredTriggerType.STATE_CHANGE.code, body.typeCode());
        assertEquals(20, body.typeCode()); // official STATE_CHANGE
        assertArrayEquals(new int[] {1}, body.params()); // official [mode]; the source is the slot
        assertArrayEquals(new int[] {500}, body.selectedIds());
        assertEquals(WiredEvent.Type.FURNI_STATE_UPDATED.toLegacyType(), WiredTriggerType.STATE_CHANGE);
    }
}
