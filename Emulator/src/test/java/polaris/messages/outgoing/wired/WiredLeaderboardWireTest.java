package polaris.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.wired.leaderboard.WiredLeaderboardChunk;
import polaris.hotel.wired.leaderboard.WiredLeaderboardConfig;
import polaris.hotel.wired.leaderboard.WiredLeaderboardDescriptor;
import polaris.hotel.wired.leaderboard.WiredLeaderboardRow;
import polaris.messages.outgoing.MessageComposer;

/** The variable leaderboard packets keep the official AIR field order. */
class WiredLeaderboardWireTest {

    @Test
    void theSnapshotListsEveryPanel() {
        WiredLeaderboardDescriptor descriptor = new WiredLeaderboardDescriptor(
                55,
                9,
                "Top",
                "Desc",
                4,
                5,
                -3,
                20,
                1,
                List.of(new WiredLeaderboardConfig.Column(0, "Username", 3)),
                List.of(new WiredLeaderboardDescriptor.Choice("812", "Coins")),
                "812",
                2);
        WireReader reader = compose(new WiredLeaderboardSnapshotComposer(List.of(descriptor)));

        assertEquals(1, reader.readInt());
        assertEquals(55, reader.readInt());
        assertEquals(9, reader.readInt());
        assertEquals("Top", reader.readString());
        assertEquals("Desc", reader.readString());
        assertEquals(4, reader.readInt());
        assertEquals(5, reader.readInt());
        assertEquals(-3, reader.readInt());
        assertEquals(20, reader.readInt());
        assertEquals(1, reader.readInt());
        assertEquals(1, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals("Username", reader.readString());
        assertEquals(3, reader.readInt());
        assertEquals(1, reader.readInt());
        assertEquals("812", reader.readString());
        assertEquals("Coins", reader.readString());
        assertEquals("812", reader.readString());
        assertEquals(2, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void chunkRowsWriteTimesAsSixtyFourBitsAndOptionalLevelAndText() {
        long created = 1_760_000_000_000L;
        WiredLeaderboardChunk chunk = new WiredLeaderboardChunk(
                1,
                51,
                List.of(new WiredLeaderboardRow(51, 7, "Bob", "hd-180-1", "300", created, created + 1000, 4, null)));
        WireReader reader = compose(new WiredLeaderboardChunkComposer(55, 9, "812", chunk));

        assertEquals(55, reader.readInt());
        assertEquals(9, reader.readInt());
        assertEquals("812", reader.readString());
        assertEquals(1, reader.readInt());
        assertEquals(51, reader.readInt());
        assertEquals(1, reader.readInt());
        assertEquals(51, reader.readInt());
        assertEquals(7, reader.readInt());
        assertEquals("Bob", reader.readString());
        assertEquals("hd-180-1", reader.readString());
        assertEquals("300", reader.readString());
        assertEquals(created, reader.readLong());
        assertEquals(created + 1000, reader.readLong());
        assertTrue(reader.readBoolean());
        assertEquals("4", reader.readString());
        assertFalse(reader.readBoolean());
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
