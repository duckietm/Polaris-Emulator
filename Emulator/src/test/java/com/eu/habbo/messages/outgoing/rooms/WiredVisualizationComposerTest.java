package com.eu.habbo.messages.outgoing.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.WiredHighlight;
import com.eu.habbo.habbohotel.rooms.WiredUserTransparency;
import com.eu.habbo.habbohotel.rooms.WiredVisualizationEntry;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.ByteBuf;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredVisualizationComposerTest {

    @Test
    void furniHighlightListsTheIdsThenTheHighlightOnItsOwnHeader() {
        ByteBuf packet = body(
                new WiredFurniHighlightComposer(List.of(12, -7, 12), new WiredHighlight(3, 60, 0x12AB34, 1, 2)), 9468);

        assertEquals(2, packet.readInt());
        assertEquals(-7, packet.readInt());
        assertEquals(12, packet.readInt());
        assertHighlight(packet, 3, 60, 0x12AB34, 1, 2);
        assertFalse(packet.isReadable());
    }

    @Test
    void brightenGoesOutAtTwiceTheStrengthTheEditorSaved() {
        ByteBuf packet = body(new WiredUserHighlightComposer(List.of(4), new WiredHighlight(4, 38, 0, 0, 0)), 1684);

        assertEquals(1, packet.readInt());
        assertEquals(4, packet.readInt());
        assertHighlight(packet, 4, 76, 0xFFFFFF, 1, 0);
        assertFalse(packet.isReadable());
    }

    @Test
    void userTransparencyCarriesTheIndicesTransparencyClickThroughAndEasing() {
        ByteBuf packet =
                body(new WiredUserTransparencyComposer(List.of(9, 3), new WiredUserTransparency(100, true, 3)), 3664);

        assertEquals(2, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals(9, packet.readInt());
        assertEquals(100, packet.readInt());
        assertTrue(packet.readBoolean());
        assertEquals(3, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void furniStateHasAnEmptyTransparencyListAndTheHighlights() {
        ByteBuf packet = body(
                new WiredFurniVisualizationStateComposer(
                        List.of(),
                        List.of(
                                new WiredVisualizationEntry<>(-5, new WiredHighlight(2, 40, 0xFF0000, 0, 1)),
                                new WiredVisualizationEntry<>(8, new WiredHighlight(4, 50, 0, 0, 0)))),
                1826);

        assertEquals(0, packet.readInt());
        assertEquals(2, packet.readInt());
        assertEquals(-5, packet.readInt());
        assertHighlight(packet, 2, 40, 0, 0, 1);
        assertEquals(8, packet.readInt());
        assertHighlight(packet, 4, 100, 0xFFFFFF, 1, 0);
        assertFalse(packet.isReadable());
    }

    @Test
    void userStateCarriesTransparencyThenHighlightsByRoomIndex() {
        ByteBuf packet = body(
                new WiredUserVisualizationStateComposer(
                        List.of(new WiredVisualizationEntry<>(6, new WiredUserTransparency(70, false, 2))),
                        List.of(new WiredVisualizationEntry<>(6, new WiredHighlight(5, 30, 0x00FF00, 1, 0)))),
                1454);

        assertEquals(1, packet.readInt());
        assertEquals(6, packet.readInt());
        assertEquals(70, packet.readInt());
        assertFalse(packet.readBoolean());
        assertEquals(2, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(6, packet.readInt());
        assertHighlight(packet, 5, 30, 0x00FF00, 1, 0);
        assertFalse(packet.isReadable());
    }

    private static ByteBuf body(MessageComposer composer, int header) {
        ByteBuf packet = composer.compose().get();
        packet.skipBytes(4);
        assertEquals(header, packet.readShort());
        return packet;
    }

    private static void assertHighlight(ByteBuf packet, int type, int strength, int color, int animation, int easing) {
        assertEquals(type, packet.readInt());
        assertEquals(strength, packet.readInt());
        assertEquals(color, packet.readInt());
        assertEquals(animation, packet.readInt());
        assertEquals(easing, packet.readInt());
    }
}
