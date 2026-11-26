package polaris.messages.outgoing.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.UserItem;

/** The name window's packet carries the packaged pet as the official client reads it. */
class PetPackageComposerTest {
    private static UserItem item() {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(77);
        return item;
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        byte[] bytes = new byte[length];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ByteBuf body(PetPackageComposer composer) {
        ByteBuf packet = composer.compose().get();
        packet.skipBytes(6); // length and header
        return packet;
    }

    @Test
    void aPackagedPetIsSentAsTypePaletteColourAndNoParts() {
        PetPackageComposer composer = PetPackageComposer.of(item(), "15 3 FF8800");
        assertInstanceOf(PetPackageWithFigureComposer.class, composer);

        ByteBuf packet = body(composer);
        assertEquals(77, packet.readInt());
        assertEquals(15, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals("FF8800", readString(packet));
        assertEquals(0, packet.readInt());
        assertEquals(0, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void withoutAFigureOnlyTheItemIsSent() {
        for (String figure : new String[] {null, "", "15 3", "x 3 FF8800"}) {
            ByteBuf packet = body(PetPackageComposer.of(item(), figure));
            assertEquals(77, packet.readInt());
            assertFalse(packet.isReadable(), String.valueOf(figure));
        }
    }
}
