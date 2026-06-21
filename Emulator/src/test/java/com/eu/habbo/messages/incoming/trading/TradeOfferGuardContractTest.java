package com.eu.habbo.messages.incoming.trading;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeOfferGuardContractTest {
    private static String incomingSource(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/trading/" + name + ".java"));
    }

    private static String roomTradeSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java"));
    }

    @Test
    void multipleTradeOfferPacketBoundsClientSuppliedCountBeforeInventoryLookups() throws Exception {
        String source = incomingSource("TradeOfferMultipleItemsEvent");

        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("count <= 0 || count > RoomTrade.MAX_OFFERED_ITEMS", count);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", count);
        int lookup = source.indexOf("getHabboItem(itemId)", loop);

        assertTrue(count > -1, "TradeOfferMultipleItemsEvent must read the client supplied count");
        assertTrue(guard > count, "TradeOfferMultipleItemsEvent must validate the count after reading it");
        assertTrue(guard < loop, "TradeOfferMultipleItemsEvent must validate the count before looping");
        assertTrue(loop < lookup, "TradeOfferMultipleItemsEvent should only resolve inventory items inside the bounded loop");
        assertTrue(source.contains("itemId <= 0"),
                "TradeOfferMultipleItemsEvent must skip invalid item ids before inventory lookup");
    }

    @Test
    void roomTradeEnforcesServerSideOfferedItemCapBeforeInventoryMutation() throws Exception {
        String source = roomTradeSource();

        int constant = source.indexOf("MAX_OFFERED_ITEMS = 100");
        int singleGuard = source.indexOf("user.getItems().size() >= MAX_OFFERED_ITEMS");
        int multipleGuard = source.indexOf("user.getItems().size() >= MAX_OFFERED_ITEMS", singleGuard + 1);
        int remove = source.indexOf("removeHabboItem(item)", multipleGuard);

        assertTrue(constant > -1, "RoomTrade must define a server-side offered item cap");
        assertTrue(singleGuard > constant, "RoomTrade.offerItem must enforce the item cap");
        assertTrue(multipleGuard > singleGuard, "RoomTrade.offerMultipleItems must enforce the item cap");
        assertTrue(multipleGuard < remove, "RoomTrade must enforce the cap before mutating inventory");
    }
}
