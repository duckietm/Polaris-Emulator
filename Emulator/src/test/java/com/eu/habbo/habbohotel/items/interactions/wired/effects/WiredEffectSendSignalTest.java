package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.installHotel;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The antenna slot offers the picked antennas (official source 100) and the triggering furni (0);
 * stored internally as 0 and 1. Picking antennas
 * used to overwrite either choice with the first picked antenna's id, so "triggering furni" could
 * not be kept once an antenna was in the list.
 */
class WiredEffectSendSignalTest {
    private static final int ANTENNA_PICKED = 0;
    private static final int ANTENNA_TRIGGER = 1;

    @Test
    void theTriggeringFurniChoiceSurvivesPickedAntennas() throws Exception {
        Room room = mock(Room.class);
        antenna(room, 401);
        WiredEffectSendSignal box = new WiredEffectSendSignal(1, 1, base(), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);

            box.saveData(slots(new WiredSettings(new int[] {0, 0}, "", new int[] {401}, 0), 0), null);
        }

        assertEquals(ANTENNA_TRIGGER, json(box).get("antennaSource").getAsInt());
    }

    @Test
    void thePickedChoiceStillFallsBackToTheFirstPickedAntenna() throws Exception {
        Room room = mock(Room.class);
        antenna(room, 401);
        WiredEffectSendSignal box = new WiredEffectSendSignal(1, 1, base(), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);

            box.saveData(slots(new WiredSettings(new int[] {0, 0}, "", new int[] {401}, 0), 100), null);
        }

        assertEquals(401, json(box).get("antennaSource").getAsInt());
    }

    @Test
    void aReloadKeepsTheTriggeringFurniChoiceToo() throws Exception {
        Room room = mock(Room.class);
        antenna(room, 401);
        WiredEffectSendSignal box = new WiredEffectSendSignal(1, 1, base(), "", 0, 0);

        box.loadWiredData(row(stored(ANTENNA_TRIGGER)), room);
        assertEquals(ANTENNA_TRIGGER, json(box).get("antennaSource").getAsInt());

        box.loadWiredData(row(stored(ANTENNA_PICKED)), room);
        assertEquals(401, json(box).get("antennaSource").getAsInt());
    }

    @Test
    void theOfficialLayoutCarriesTheSplitsTheSlotsAndTheForwardPicks() throws Exception {
        Room room = mock(Room.class);
        antenna(room, 401);
        HabboItem forward = mock(HabboItem.class);
        when(forward.getId()).thenReturn(402);
        when(room.getHabboItem(402)).thenReturn(forward);
        WiredEffectSendSignal box = new WiredEffectSendSignal(1, 1, base(), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);

            WiredSettings settings = slots(new WiredSettings(new int[] {1, 0}, "", new int[] {401}, 0), 100);
            settings.setFurniSourceTypes(new int[] {100, 100});
            settings.setUserSourceTypes(new int[] {200});
            settings.setFurniIds2(new int[] {402});
            box.saveData(settings, null);
        }

        assertEquals(true, json(box).get("signalPerFurni").getAsBoolean());
        assertEquals(false, json(box).get("signalPerUser").getAsBoolean());
        assertEquals(100, json(box).get("furniForward").getAsInt());
        assertEquals(200, json(box).get("userForward").getAsInt());
        assertEquals(402, json(box).getAsJsonArray("forwardItemIds").get(0).getAsInt());
        assertArrayEquals(new int[] {100, 100}, box.officialFurniSources());
        assertArrayEquals(new int[] {200}, box.officialUserSources());
        assertArrayEquals(new int[] {402}, box.officialFurniIds2());
    }

    /** The official antenna slot: 0 the triggering furni, 100 the picks. */
    private static WiredSettings slots(WiredSettings settings, int antennaSlot) {
        settings.setFurniSourceTypes(new int[] {antennaSlot});
        return settings;
    }

    @Test
    void aNonAntennaPickIsRefusedWithTheOfficialKey() {
        Room room = mock(Room.class);
        HabboItem chair = mock(HabboItem.class);
        when(chair.getId()).thenReturn(402);
        Item chairBase = base();
        when(chair.getBaseItem()).thenReturn(chairBase);
        when(room.getHabboItem(402)).thenReturn(chair);
        WiredEffectSendSignal box = new WiredEffectSendSignal(1, 1, base(), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);

            com.eu.habbo.messages.incoming.wired.WiredSaveException refused =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            com.eu.habbo.messages.incoming.wired.WiredSaveException.class,
                            () -> box.saveData(
                                    slots(new WiredSettings(new int[] {0, 0}, "", new int[] {402}, 0), 100), null));
            assertEquals("wiredfurni.error.require_antenna_furni", refused.getMessage());
        }
    }

    @Test
    void aSignalChainReachesEachAntennaOnlyOnce() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile(anyShort(), anyShort())).thenReturn(new RoomTile());
        antenna(room, 401);
        HabboItem antenna = room.getHabboItem(401);
        List<WiredEvent> sent = new ArrayList<>();
        WiredEffectSendSignal box = new WiredEffectSendSignal(1, 1, base(), "", 0, 0) {
            @Override
            protected boolean dispatchSignalEvent(WiredEvent event) {
                sent.add(event);
                return true;
            }
        };
        box.loadWiredData(row(stored(ANTENNA_TRIGGER)), room);

        // A stack that is not a signal sends to the antenna; the signal names the antenna in its path.
        box.execute(signalContext(room, antenna, Set.of()));
        assertEquals(1, sent.size());
        assertEquals(Set.of(401), sent.get(0).getSignalPath());

        // The receiving stack signalling its own antenna again, or a cycle back to it, is dropped.
        box.execute(signalContext(room, antenna, Set.of(401)));
        box.execute(signalContext(room, antenna, Set.of(405, 401)));
        assertEquals(1, sent.size());

        box.execute(signalContext(room, antenna, Set.of(405)));
        assertEquals(2, sent.size());
        assertTrue(sent.get(1).getSignalPath().containsAll(Set.of(405, 401)));
    }

    private static WiredContext signalContext(Room room, HabboItem antenna, Set<Integer> path) {
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .sourceItem(antenna)
                .signalPath(path)
                .build();
        return new WiredContext(event, null, mock(WiredServices.class), new WiredState(20));
    }

    private static String stored(int antennaSource) {
        return "{\"delay\":0,\"itemIds\":[401],\"forwardItemIds\":[],\"antennaSource\":" + antennaSource
                + ",\"furniForward\":0,\"userForward\":0,\"signalPerFurni\":false,\"signalPerUser\":false,"
                + "\"channel\":0}";
    }

    private static void antenna(Room room, int id) {
        ItemInteraction interaction = mock(ItemInteraction.class);
        when(interaction.getName()).thenReturn("antenna");
        Item base = base();
        when(base.getInteractionType()).thenReturn(interaction);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        when(room.getHabboItem(id)).thenReturn(item);
    }
}
