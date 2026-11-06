package polaris.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.messages.outgoing.MessageComposer;

/** The furniture label packets keep the official AIR field order. */
class WiredLabelWireTest {

    private static WiredExtraLabel label() {
        WiredExtraLabel label = new WiredExtraLabel(42, 1, null, "", 0, 0);
        label.setText("Line one\nline two");
        label.setSettings(2651759, WiredExtraLabel.VISIBILITY_EVERYONE, true);
        return label;
    }

    @Test
    void theListWritesTypeIdTextAndSettings() {
        WireReader reader = compose(new WiredLabelsComposer(List.of(label())));

        assertEquals(1, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(42, reader.readInt());
        assertEquals("Line one\nline two", reader.readString());
        assertEquals(2651759, reader.readInt());
        assertEquals(0, reader.readInt());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void theUpdateWritesChangesThenRemovedIds() {
        WireReader reader = compose(new WiredLabelsUpdateComposer(List.of(), List.of(42, 43)));

        assertEquals(0, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(42, reader.readInt());
        assertEquals(43, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    private static WireReader compose(MessageComposer composer) {
        ByteBuf packet = composer.compose().get();
        try {
            byte[] bytes = new byte[packet.readableBytes()];
            packet.getBytes(packet.readerIndex(), bytes);
            return new WireReader(bytes);
        } finally {
            packet.release();
        }
    }

    /** Reads the framed packet back: four-byte length, two-byte header, then the body. */
    private static final class WireReader {
        private final byte[] bytes;
        private int position = 6;

        WireReader(byte[] bytes) {
            this.bytes = bytes;
        }

        int readInt() {
            int value = ((this.bytes[this.position] & 0xff) << 24)
                    | ((this.bytes[this.position + 1] & 0xff) << 16)
                    | ((this.bytes[this.position + 2] & 0xff) << 8)
                    | (this.bytes[this.position + 3] & 0xff);
            this.position += 4;
            return value;
        }

        long readLong() {
            long high = this.readInt();
            long low = this.readInt() & 0xffffffffL;
            return (high << 32) | low;
        }

        boolean readBoolean() {
            return this.bytes[this.position++] != 0;
        }

        String readString() {
            int length = ((this.bytes[this.position] & 0xff) << 8) | (this.bytes[this.position + 1] & 0xff);
            this.position += 2;
            String value = new String(this.bytes, this.position, length, StandardCharsets.UTF_8);
            this.position += length;
            return value;
        }

        int remaining() {
            return this.bytes.length - this.position;
        }
    }
}
