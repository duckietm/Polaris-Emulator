package polaris.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import polaris.messages.ClientMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.plugins.PluginChannelMessageComposer;

/**
 * The generic plugin channel on the wire: two strings, the channel and the payload. The fixture is the one the
 * renderer's PluginChannelProtocol test pins, so both ends agree byte for byte.
 */
class PluginChannelWireTest {
    /** "rp.job" and {"a":1}, each a short length and UTF-8 bytes. */
    private static final String MESSAGE_HEX = "000672702e6a6f6200077b2261223a317d";

    @Test
    void writesTheChannelThenThePayload() {
        ByteBuf frame = new PluginChannelMessageComposer("rp.job", "{\"a\":1}")
                .compose()
                .get();

        assertEquals(Outgoing.PluginChannelMessageComposer, frame.getShort(4));
        // Frame length (4) and header (2) come first.
        assertEquals(MESSAGE_HEX, ByteBufUtil.hexDump(frame, 6, frame.readableBytes() - 6));
    }

    @Test
    void readsWhatTheClientWrites() {
        ClientMessage message = new ClientMessage(
                Incoming.PluginChannelActionEvent,
                Unpooled.wrappedBuffer(HexFormat.of().parseHex(MESSAGE_HEX)));

        assertEquals("rp.job", message.readString());
        assertEquals("{\"a\":1}", message.readString());
    }

    @Test
    void channelNamesAreShortLowerCaseAndPlain() {
        assertTrue(PluginChannel.isValidChannel("rp.job"));
        assertTrue(PluginChannel.isValidChannel("casino-table_2"));
        assertTrue(PluginChannel.isValidChannel("a".repeat(64)));

        assertFalse(PluginChannel.isValidChannel(null));
        assertFalse(PluginChannel.isValidChannel(""));
        assertFalse(PluginChannel.isValidChannel("RP.job"));
        assertFalse(PluginChannel.isValidChannel(".hidden"));
        assertFalse(PluginChannel.isValidChannel("rp job"));
        assertFalse(PluginChannel.isValidChannel("a".repeat(65)));
    }

    @Test
    void payloadsHaveACeiling() {
        assertTrue(PluginChannel.isValidPayload(""));
        assertTrue(PluginChannel.isValidPayload("x".repeat(PluginChannel.MAX_PAYLOAD)));
        assertFalse(PluginChannel.isValidPayload("x".repeat(PluginChannel.MAX_PAYLOAD + 1)));
        assertFalse(PluginChannel.isValidPayload(null));
    }

    @Test
    void sendingToNobodySendsNothing() {
        assertFalse(PluginChannel.send(null, "rp.job", "{}"));
    }

    @Test
    void theHeadersSitInTheirOwnRange() {
        assertEquals(10200, Outgoing.PluginChannelMessageComposer);
        assertEquals(10201, Incoming.PluginChannelActionEvent);
    }
}
