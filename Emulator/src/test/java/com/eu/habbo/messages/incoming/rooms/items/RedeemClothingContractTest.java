package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RedeemClothingContractTest {
    private static String source() throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemClothingEvent.java"));
    }

    @Test
    void clothingIsGrantedBeforeVoucherFurnitureIsConsumed() throws Exception {
        String source = source();

        int claim = source.indexOf("room.claimItem(item)");
        int grantCall = source.indexOf("this.grantClothing(clothing.id)");
        int putBack = source.indexOf("room.addHabboItem(item)", grantCall);
        int deleteItem = source.indexOf("new QueryDeleteHabboItem(item.getId())");

        assertTrue(
                source.contains("private boolean grantClothing(int clothingId)"),
                "clothing DB insert should report whether the grant succeeded");
        // The furni is claimed out of the room first, so a pickup cannot race the grant; a failed grant puts it back.
        assertTrue(claim > -1 && claim < grantCall, "the furni is claimed before the clothing is granted");
        assertTrue(putBack > grantCall, "a failed grant puts the furni back in the room");
        assertTrue(grantCall < deleteItem, "voucher furniture must not be deleted before the clothing grant succeeds");
    }
}
