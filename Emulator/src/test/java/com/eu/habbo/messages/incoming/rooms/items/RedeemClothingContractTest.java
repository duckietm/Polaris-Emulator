package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RedeemClothingContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemClothingEvent.java"));
    }

    @Test
    void clothingIsGrantedBeforeVoucherFurnitureIsConsumed() throws Exception {
        String source = source();

        int grantCall = source.indexOf("grantClothing(");
        int roomRemoval = source.indexOf("removeHabboItem(item)");
        int deleteItem = source.indexOf("new QueryDeleteHabboItem(item.getId())");

        assertTrue(source.contains("private boolean grantClothing(int clothingId)"),
                "clothing DB insert should report whether the grant succeeded");
        assertTrue(grantCall > -1, "redeem path should call grantClothing before consuming the item");
        assertTrue(grantCall < roomRemoval, "room item must not be removed before the clothing grant succeeds");
        assertTrue(grantCall < deleteItem, "voucher furniture must not be deleted before the clothing grant succeeds");
    }
}
