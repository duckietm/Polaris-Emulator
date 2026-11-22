package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.handshake.HotelSettingsComposer;

class HotelSettingsPacketContractTest {

    @Test
    void carriesTheVersionThePhotoAddressTheLimitsAndTheAliasesInOrder() {
        ByteBuf packet = new HotelSettingsComposer(
                        "http://localhost/camera/",
                        100,
                        38,
                        15,
                        16,
                        "all,everyone,tutti",
                        "friends,amici",
                        "room,stanza")
                .compose()
                .get();

        packet.skipBytes(4);
        assertEquals(Outgoing.HotelSettingsComposer, packet.readShort());
        assertEquals(HotelSettingsComposer.PROTOCOL_VERSION, packet.readInt());
        assertEquals("http://localhost/camera/", readString(packet));
        assertEquals(100, packet.readInt());
        assertEquals(38, packet.readInt());
        assertEquals(15, packet.readInt());
        assertEquals(16, packet.readInt());
        assertEquals("all,everyone,tutti", readString(packet));
        assertEquals("friends,amici", readString(packet));
        assertEquals("room,stanza", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void sendsEmptyStringsForMissingValues() {
        ByteBuf packet = new HotelSettingsComposer(null, 0, 0, 0, 0, null, null, null)
                .compose()
                .get();

        packet.skipBytes(6);
        assertEquals(HotelSettingsComposer.PROTOCOL_VERSION, packet.readInt());
        assertEquals("", readString(packet));
        packet.skipBytes(16);
        assertEquals("", readString(packet));
        assertEquals("", readString(packet));
        assertEquals("", readString(packet));
        assertFalse(packet.isReadable());
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        byte[] bytes = new byte[length];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
