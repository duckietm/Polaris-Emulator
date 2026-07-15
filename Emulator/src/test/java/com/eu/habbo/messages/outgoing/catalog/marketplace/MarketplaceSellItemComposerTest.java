package com.eu.habbo.messages.outgoing.catalog.marketplace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarketplaceSellItemComposerTest {
    @Test
    void encodesOnlyResultCodeAndTokenCountExpectedByTheClient() {
        var packet = new MarketplaceSellItemComposer(1, 7).compose().get();
        packet.skipBytes(6);

        assertEquals(1, packet.readInt());
        assertEquals(7, packet.readInt());
        assertFalse(packet.isReadable());
    }
}
