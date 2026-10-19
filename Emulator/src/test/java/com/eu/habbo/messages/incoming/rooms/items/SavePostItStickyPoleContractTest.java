package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SavePostItStickyPoleContractTest {

    private static String source() throws Exception {
        // Line wrapping (formatter) does not change the code these contracts look for.
        return Files.readString(Path.of(
                        "src/main/java/com/eu/habbo/messages/incoming/rooms/items/SavePostItStickyPoleEvent.java"))
                .replaceAll("\\(\\s+", "(")
                .replaceAll("\\s*\\n\\s*\\.", ".")
                .replaceAll(",\\s*\\n\\s*", ", ");
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

        assertTrue(source.contains(
                "PostItColor.isCustomColor(color) || color.equalsIgnoreCase(PostItColor.YELLOW.hexColor)"));
        assertTrue(source.contains("PostItColor.randomColorNotYellow().hexColor"));
    }
}
