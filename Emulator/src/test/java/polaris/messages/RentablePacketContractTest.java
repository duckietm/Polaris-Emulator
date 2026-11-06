package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.rooms.items.rentablespaces.FurniRentOrBuyoutOfferComposer;
import polaris.messages.outgoing.rooms.items.rentablespaces.RentableSpaceInfoComposer;
import polaris.messages.outgoing.rooms.items.rentablespaces.RentableSpaceRentFailedComposer;
import polaris.messages.outgoing.rooms.items.rentablespaces.RentableSpaceRentOkComposer;

/** Wire contract of the AIR 13 rentable space widget and rent / buy-out flow (class_1956 header table). */
class RentablePacketContractTest {

    @Test
    void headersMatchTheClientRegistry() {
        assertEquals(872, Incoming.GetRentableSpaceStatusEvent);
        assertEquals(2946, Incoming.RentSpaceEvent);
        assertEquals(1667, Incoming.RentSpaceCancelEvent);
        assertEquals(2518, Incoming.GetRentOrBuyoutOfferEvent);
        assertEquals(1071, Incoming.ExtendRentOrBuyoutFurniEvent);
        assertEquals(2115, Incoming.ExtendRentOrBuyoutStripItemEvent);
        assertEquals(3559, Outgoing.RentableSpaceInfoComposer);
        assertEquals(2046, Outgoing.RentableSpaceRentOkComposer);
        assertEquals(1868, Outgoing.RentableSpaceRentFailedComposer);
        assertEquals(35, Outgoing.FurniRentOrBuyoutOfferComposer);
    }

    @Test
    void rentOkCarriesTheSecondsLeft() {
        ByteBuf packet = new RentableSpaceRentOkComposer(604800).compose().get();
        packet.skipBytes(4);

        assertEquals(2046, packet.readShort());
        assertEquals(604800, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void rentFailedCarriesTheWidgetErrorCode() {
        ByteBuf packet = new RentableSpaceRentFailedComposer(RentableSpaceInfoComposer.CANT_RENT_NO_CLUB)
                .compose()
                .get();
        packet.skipBytes(4);

        assertEquals(1868, packet.readShort());
        assertEquals(203, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void rentOrBuyoutOfferEchoesTheRequestThenThePrice() {
        ByteBuf packet = new FurniRentOrBuyoutOfferComposer(false, "rentable_sofa", true, 25, 10, 5)
                .compose()
                .get();
        packet.skipBytes(4);

        assertEquals(35, packet.readShort());
        assertFalse(packet.readBoolean());
        assertEquals("rentable_sofa", readString(packet));
        assertTrue(packet.readBoolean());
        assertEquals(25, packet.readInt());
        assertEquals(10, packet.readInt());
        assertEquals(5, packet.readInt());
        assertFalse(packet.isReadable());
    }

    private static String readString(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
