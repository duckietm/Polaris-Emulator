package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CatalogPurchaseCommandTest {

    @Test
    void readsPurchaseFieldsInProtocolOrder() {
        CatalogPurchaseCommand command = CatalogPurchaseCommandReader.readFrom(packet(17, 42, "blue\npet", 3));

        assertAll(
                () -> assertEquals(17, command.pageId()),
                () -> assertEquals(42, command.itemId()),
                () -> assertEquals("blue\npet", command.extraData()),
                () -> assertEquals(3, command.count()));
    }

    @Test
    void clampsRequestedCountToLegacyRange() {
        assertAll(
                () -> assertEquals(
                        1,
                        CatalogPurchaseCommandReader.readFrom(packet(1, 2, "", -5))
                                .count()),
                () -> assertEquals(
                        1,
                        CatalogPurchaseCommandReader.readFrom(packet(1, 2, "", 0))
                                .count()),
                () -> assertEquals(
                        1,
                        CatalogPurchaseCommandReader.readFrom(packet(1, 2, "", 1))
                                .count()),
                () -> assertEquals(
                        100,
                        CatalogPurchaseCommandReader.readFrom(packet(1, 2, "", 100))
                                .count()),
                () -> assertEquals(
                        100,
                        CatalogPurchaseCommandReader.readFrom(packet(1, 2, "", 101))
                                .count()));
    }

    private static ClientMessage packet(int pageId, int itemId, String extraData, int count) {
        byte[] extraDataBytes = extraData.getBytes(StandardCharsets.UTF_8);
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(pageId);
        buffer.writeInt(itemId);
        buffer.writeShort(extraDataBytes.length);
        buffer.writeBytes(extraDataBytes);
        buffer.writeInt(count);
        return new ClientMessage(0, buffer);
    }
}
