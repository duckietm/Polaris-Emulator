package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RedeemItemCurrencyContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemItemEvent.java"));
    }

    @Test
    void diamondFurnitureCreditsTheDiamondCurrencyExplicitly() throws Exception {
        String source = source();
        int diamondCase = source.indexOf("case FurnitureRedeemedEvent.DIAMONDS:");
        int nextCase = source.indexOf("case FurnitureRedeemedEvent.PIXELS:", diamondCase);
        String diamondBranch = source.substring(diamondCase, nextCase);

        assertTrue(diamondCase > -1, "diamond redemption branch must exist");
        assertTrue(diamondBranch.contains("givePoints(FurnitureRedeemedEvent.DIAMONDS, furniRedeemEvent.amount)"),
                "diamond furniture must not depend on seasonal.primary.type");
    }
}
