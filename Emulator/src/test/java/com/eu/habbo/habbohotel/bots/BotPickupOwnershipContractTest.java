package com.eu.habbo.habbohotel.bots;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BotPickupOwnershipContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/bots/BotManager.java"));
    }

    @Test
    void roomOwnerPickupReturnsBotToOriginalOwner() throws Exception {
        String source = source();

        assertTrue(source.contains("HabboInfo receiverInfo = resolvePickupReceiver(bot, habbo);"),
                "bot pickup should resolve the receiver without blindly using the picker");
        assertTrue(source.contains("private HabboInfo resolvePickupReceiver(Bot bot, Habbo picker)"),
                "bot pickup receiver logic should be centralized");
        assertTrue(source.contains("return Emulator.getGameEnvironment().getHabboManager().getHabboInfo(bot.getOwnerId());"),
                "when a room owner picks up someone else's bot, it should return to the original bot owner");
        assertTrue(source.contains("Room botRoom = bot.getRoom();"),
                "pickup should remove the bot from the bot's current room, not the receiver's current room");
        assertTrue(source.contains("botRoom.removeBot(bot);"),
                "bot removal should work even when the original owner is offline");
    }
}
