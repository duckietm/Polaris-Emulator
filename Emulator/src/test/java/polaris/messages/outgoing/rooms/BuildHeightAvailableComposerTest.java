package polaris.messages.outgoing.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;

class BuildHeightAvailableComposerTest {
    @Test
    void encodesAvailabilityAndTheSliderRangeInRendererOrder() {
        var packet = new BuildHeightAvailableComposer(true).compose().get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertEquals(0, packet.readInt());
        assertEquals((int) Room.MAXIMUM_FURNI_HEIGHT, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void tellsTheClientWhenTheWidgetMayNotBeUsed() {
        var packet = new BuildHeightAvailableComposer(false).compose().get();
        packet.skipBytes(6);

        assertFalse(packet.readBoolean());
    }
}
