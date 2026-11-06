package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.wired.WiredClickSettingsComposer;

/** The click-settings box tells the selected players' clients, and only them, what their clicks do. */
class WiredEffectClickSettingsTest {

    private static WiredSettings settings(int... params) {
        WiredSettings settings = mock(WiredSettings.class);
        when(settings.getIntParams()).thenReturn(params);
        when(settings.getStringParam()).thenReturn("");
        when(settings.getFurniIds()).thenReturn(new int[0]);
        when(settings.getDelay()).thenReturn(0);
        return settings;
    }

    @Test
    void savesTheTwoOptionsAndTheUserSourceClampedToWhatTheClientKnows() throws Exception {
        WiredEffectClickSettings box = new WiredEffectClickSettings(1, 1, base(), "", 0, 0);

        WiredSettings picked = settings(2, 1);
        when(picked.userSource(0, 0)).thenReturn(200); // the official user source slot
        box.saveData(picked, null);

        assertEquals(2, json(box).get("userOption").getAsInt());
        assertEquals(1, json(box).get("furniOption").getAsInt());
        assertEquals(200, json(box).get("userSource").getAsInt());

        box.saveData(settings(9, 7), null);

        assertEquals(0, json(box).get("userOption").getAsInt());
        assertEquals(0, json(box).get("furniOption").getAsInt());
        assertEquals(0, json(box).get("userSource").getAsInt());
    }

    @Test
    void refusesASaveWithoutBothOptions() {
        WiredEffectClickSettings box = new WiredEffectClickSettings(1, 1, base(), "", 0, 0);

        assertThrows(WiredSaveException.class, () -> box.saveData(settings(1), null));
    }

    @Test
    void aStoredRowLoadsClampedAndALegacyRowLoadsTheDefaults() throws Exception {
        WiredEffectClickSettings box = new WiredEffectClickSettings(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"delay\":3,\"userOption\":2,\"furniOption\":5,\"userSource\":11}"), null);

        assertEquals(2, box.getUserOption());
        assertEquals(0, box.getFurniOption());
        assertEquals(3, box.getDelay());

        box.loadWiredData(row("0\t1"), null);

        assertEquals(0, box.getUserOption());
        assertEquals(0, box.getDelay());
    }

    @Test
    void sendsTheSettingsToTheTriggeringPlayerAndSkipsWhoeverHasNoClient() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User player = user("Camwijs", unit);
        when(room.getUser(unit)).thenReturn(player);
        WiredEffectClickSettings box = new WiredEffectClickSettings(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"delay\":0,\"userOption\":1,\"furniOption\":1,\"userSource\":0}"), room);

        box.execute(context(room, unit));

        ArgumentCaptor<WiredClickSettingsComposer> sent = ArgumentCaptor.forClass(WiredClickSettingsComposer.class);
        verify(player.getClient()).sendResponse(sent.capture());
        assertEquals(WiredClickSettingsComposer.CLICK_USER_CLICK_WALK_BEHIND, box.getUserOption());
        assertEquals(WiredClickSettingsComposer.CLICK_FURNI_PASS_THROUGH, box.getFurniOption());

        // A bot in the same slot has no client and no Habbo: nothing to tell, nothing fails.
        RoomUnit bot = mock(RoomUnit.class);
        Room botRoom = mock(Room.class);
        when(botRoom.getUser(bot)).thenReturn(null);

        box.execute(context(botRoom, bot));

        verify(player.getClient(), times(1)).sendResponse(any(polaris.messages.outgoing.MessageComposer.class));
    }
}
