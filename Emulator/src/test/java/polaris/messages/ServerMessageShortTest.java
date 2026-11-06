package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ServerMessageShortTest {

    private static final int PAYLOAD_OFFSET = Integer.BYTES + Short.BYTES;

    @Test
    void writesSignedAndUnsignedShortBoundariesWithoutChangingTheirWireBits() {
        assertWireShort(Short.MIN_VALUE, 0x8000);
        assertWireShort(Short.MAX_VALUE, 0x7FFF);
        assertWireShort(0xFFFF, 0xFFFF);
    }

    @Test
    void rejectsValuesThatWouldBeTruncated() {
        ServerMessage message = new ServerMessage(1);

        assertThrows(IllegalArgumentException.class, () -> message.appendShort(Short.MIN_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> message.appendShort(0x1_0000));
    }

    private static void assertWireShort(int value, int expectedBits) {
        ServerMessage message = new ServerMessage(1);

        message.appendShort(value);

        assertEquals(expectedBits, message.get().getUnsignedShort(PAYLOAD_OFFSET));
    }
}
