package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Redeeming clothing wrote the grant before checking the tile it would clear. */
class RedeemClothingOrderContractTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemClothingEvent.java");

    @Test
    void theTileIsResolvedBeforeTheClothingIsGranted() throws Exception {
        String source = Files.readString(SOURCE);
        int tileCheck = source.indexOf("if (tile == null) {");
        int grant = source.indexOf("this.grantClothing(clothing.id)");

        assertTrue(tileCheck > 0 && grant > tileCheck);
        assertTrue(new RedeemClothingEvent().getRatelimit() > 0);
    }
}
