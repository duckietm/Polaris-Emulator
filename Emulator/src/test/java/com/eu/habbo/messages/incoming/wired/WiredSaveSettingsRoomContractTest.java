package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Effects and conditions got the room on their settings before saving, triggers did not: a trigger that checks its
 * picks against the room (Receive Link Dataframe and its capture variables) skipped the check and saved anyway.
 */
class WiredSaveSettingsRoomContractTest {

    @Test
    void everyWiredSaveHandsTheRoomToTheSettingsBeforeSaving() throws Exception {
        for (String handler :
                new String[] {"WiredTriggerSaveDataEvent", "WiredEffectSaveDataEvent", "WiredConditionSaveDataEvent"}) {
            String source = Files.readString(
                    Path.of("src/main/java/com/eu/habbo/messages/incoming/wired/" + handler + ".java"));

            int setRoom = source.indexOf("settings.setRoom(room);");
            int rules = source.indexOf("WiredSourceSaveRules.apply(");

            assertTrue(setRoom > -1, handler + " must give the settings the room");
            assertTrue(rules > setRoom, handler + " must give the settings the room before checking and saving them");
        }
    }
}
