package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SavePostItStickyPoleContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/SavePostItStickyPoleEvent.java"));
    }

    @Test
    void stickyPoleTextIsFilteredAndBoundedBeforeMutation() throws Exception {
        String source = source();

        int textRead = source.indexOf("WordFilter().filter");
        int charLimit = source.indexOf("text.length() > Emulator.getConfig().getInt(\"postit.charlimit\")", textRead);
        int update = source.indexOf("sticky.setExtradata", textRead);

        assertTrue(textRead > -1, "Sticky pole text should pass through the word filter");
        assertTrue(charLimit > textRead, "Sticky pole text must enforce the configured post-it char limit");
        assertTrue(charLimit < update, "Sticky pole text must be bounded before item mutation");
    }

    @Test
    void stickyPoleOnlyMutatesPostItItemsInARoom() throws Exception {
        String source = source();

        assertTrue(source.contains("if (room == null)"));
        assertTrue(source.contains("sticky instanceof InteractionPostIt"));
    }

    @Test
    void stickyPoleNormalizesCustomOrYellowColors() throws Exception {
        String source = source();

        assertTrue(source.contains("PostItColor.isCustomColor(color) || color.equalsIgnoreCase(PostItColor.YELLOW.hexColor)"));
        assertTrue(source.contains("PostItColor.randomColorNotYellow().hexColor"));
    }
}
