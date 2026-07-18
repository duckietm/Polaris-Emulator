package com.eu.habbo.plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMutableIdGuardContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/" + path));
    }

    @Test
    void pluginMutatedButlerItemIdIsValidatedBeforeUse() throws Exception {
        String butler = source("habbohotel/bots/ButlerBot.java");
        int fireEvent = butler.indexOf("fireEvent(serveEvent)");
        int idGuard = butler.indexOf("PluginEventInputGuard.isPositiveId(serveEvent.itemId)", fireEvent);
        int handItem = butler.indexOf("new RoomUnitGiveHanditem", fireEvent);

        assertTrue(idGuard > fireEvent, "plugin-mutated hand-item id must be checked after event dispatch");
        assertTrue(handItem > idGuard, "hand-item id must be checked before task creation");
    }

    @Test
    void pluginMutatedPhotoRoomIdMustResolveBeforePersistence() throws Exception {
        String publish = source("messages/incoming/camera/CameraPublishToWebEvent.java");
        int fireEvent = publish.indexOf("fireEvent(publishPictureEvent)");
        int idGuard = publish.indexOf("PluginEventInputGuard.isPositiveId(publishPictureEvent.roomId)", fireEvent);
        int roomLookup = publish.indexOf("getRoomManager().loadRoom(publishPictureEvent.roomId)", idGuard);
        int persistence = publish.indexOf("statement.setInt(2, publishPictureEvent.roomId)", fireEvent);

        assertTrue(idGuard > fireEvent, "plugin-mutated room id must be checked after event dispatch");
        assertTrue(roomLookup > idGuard, "plugin-mutated room id must resolve server-side");
        assertTrue(persistence > roomLookup, "room validation must happen before database persistence");
    }
}
