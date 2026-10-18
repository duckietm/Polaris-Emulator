package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.wired.WiredVariablesForObjectComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredObjectInspectionTest {
    private static final int FURNI = 501;

    @Test
    void aFurniCarriesItsValuesAndTheBoxesThatPickIt() {
        Room room = roomWithFurni();

        WiredObjectInspection inspection = WiredObjectInspection.inspect(room, WiredObjectInspection.TYPE_FURNI, FURNI);

        assertEquals(Map.of("furni:10", 7, "furni:11", 0), inspection.getValues());
        assertEquals(List.of(900), inspection.getConfiguredInWireds());
    }

    @Test
    void anUnknownFurniGetsNoAnswer() {
        assertNull(WiredObjectInspection.inspect(roomWithFurni(), WiredObjectInspection.TYPE_FURNI, 9999));
    }

    @Test
    void theReplyFollowsTheOfficialLayoutForAFurni() {
        WiredObjectInspection inspection =
                new WiredObjectInspection(WiredObjectInspection.TYPE_FURNI, FURNI, Map.of("furni:10", 7), List.of(900));
        ByteBuf packet =
                new WiredVariablesForObjectComposer(inspection).compose().get();
        packet.skipBytes(6);

        assertEquals(0, packet.readInt());
        assertEquals(FURNI, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals("furni:10", readString(packet));
        assertEquals(7, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(900, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void theRoomReplyHasNoObjectIdAndNoBoxList() {
        WiredObjectInspection inspection =
                new WiredObjectInspection(WiredObjectInspection.TYPE_ROOM, 0, Map.of("room:2", 99), List.of());
        ByteBuf packet =
                new WiredVariablesForObjectComposer(inspection).compose().get();
        packet.skipBytes(6);

        assertEquals(-10, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals("room:2", readString(packet));
        assertEquals(99, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void readsPicksFromTheSavedWiredData() {
        assertTrue(WiredObjectInspection.picks("{\"delay\":0,\"itemIds\":[3,501]}", FURNI));
        assertFalse(WiredObjectInspection.picks("{\"itemIds\":[3]}", FURNI));
        assertFalse(WiredObjectInspection.picks("1\t2\t501", FURNI));
        assertFalse(WiredObjectInspection.picks("{broken", FURNI));
        assertFalse(WiredObjectInspection.picks(null, FURNI));
    }

    private static Room roomWithFurni() {
        Room room = mock(Room.class);
        when(room.getHabboItem(FURNI)).thenReturn(mock(HabboItem.class));

        RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
        when(furniVariables.createSnapshot())
                .thenReturn(new RoomFurniVariableManager.Snapshot(
                        1,
                        List.of(),
                        List.of(
                                new RoomFurniVariableManager.FurniAssignmentsEntry(
                                        FURNI,
                                        List.of(
                                                new RoomFurniVariableManager.AssignmentEntry(10, 7, 0, 0),
                                                new RoomFurniVariableManager.AssignmentEntry(11, null, 0, 0))),
                                new RoomFurniVariableManager.FurniAssignmentsEntry(
                                        600, List.of(new RoomFurniVariableManager.AssignmentEntry(10, 1, 0, 0))))));
        when(room.getFurniVariableManager()).thenReturn(furniVariables);

        InteractionWiredEffect picking = mock(InteractionWiredEffect.class);
        when(picking.getId()).thenReturn(900);
        when(picking.getWiredData()).thenReturn("{\"itemIds\":[501]}");
        InteractionWiredCondition other = mock(InteractionWiredCondition.class);
        when(other.getId()).thenReturn(901);
        when(other.getWiredData()).thenReturn("{\"itemIds\":[600]}");
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(types.getTriggers()).thenReturn(Set.of());
        when(types.getConditions()).thenReturn(Set.of(other));
        when(types.getEffects()).thenReturn(Set.of(picking));
        when(types.getExtras()).thenReturn(Set.of());
        when(room.getRoomSpecialTypes()).thenReturn(types);
        return room;
    }

    private static String readString(ByteBuf packet) {
        byte[] bytes = new byte[packet.readUnsignedShort()];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
