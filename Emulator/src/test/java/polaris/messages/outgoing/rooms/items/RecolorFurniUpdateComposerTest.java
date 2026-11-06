package polaris.messages.outgoing.rooms.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecolorFurniUpdateComposerTest {
    @Test
    void writesEachUpdateAsIdsThenTheColourAsThreeShorts() {
        var packet = new RecolorFurniUpdateComposer(List.of(
                        new RecolorFurniUpdateComposer.Update(List.of(7, 9), 0xFF8000),
                        new RecolorFurniUpdateComposer.Update(List.of(12), 0x0010FF)))
                .compose()
                .get();
        packet.skipBytes(4);

        assertEquals(181, packet.readShort());
        assertEquals(2, packet.readInt());
        assertEquals(2, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals(9, packet.readInt());
        assertEquals(255, packet.readShort());
        assertEquals(128, packet.readShort());
        assertEquals(0, packet.readShort());
        assertEquals(1, packet.readInt());
        assertEquals(12, packet.readInt());
        assertEquals(0, packet.readShort());
        assertEquals(16, packet.readShort());
        assertEquals(255, packet.readShort());
        assertFalse(packet.isReadable());
    }
}
