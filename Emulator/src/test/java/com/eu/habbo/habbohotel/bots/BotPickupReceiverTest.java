package com.eu.habbo.habbohotel.bots;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A room owner (or staff) picking up a visitor's bot wrote the visitor as its owner but put the bot in
 * the picker's inventory: two copies of the same bot id, one of them in the wrong inventory.
 */
class BotPickupReceiverTest {

    @Test
    void thePickedUpBotGoesToItsOwnersInventory() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/bots/BotManager.java"))
                .replaceAll("\\s+", " ");

        assertTrue(
                source.contains(
                        "Habbo receiver = receiverInfo.getId() == (habbo == null ? -1 : habbo.getHabboInfo().getId())"
                                + " ? habbo : Emulator.getGameEnvironment().getHabboManager().getHabbo(receiverInfo.getId());"),
                "the receiver is the owner, online, and the picker only when they are the owner");
        assertFalse(source.contains(": habbo; if (receiver != null)"), "the picker is no longer the default receiver");
    }
}
