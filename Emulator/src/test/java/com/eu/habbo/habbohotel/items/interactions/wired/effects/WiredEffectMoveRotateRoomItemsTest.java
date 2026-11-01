package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Move/Rotate Furni copied its targets before it dropped the picks the room no longer held, so a furni picked up
 * or placed in another room was still moved, and its coordinates and rotation rewritten.
 */
class WiredEffectMoveRotateRoomItemsTest {

    @Test
    void onlyFurniTheRoomStillHoldsAreMoved() throws Exception {
        String source = Files.readString(
                        Path.of(
                                "src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects/WiredEffectMoveRotateFurni.java"))
                .replaceAll("\\s+", " ");

        assertTrue(source.contains(
                "effectiveItems.removeIf(item -> item == null || room.getHabboItem(item.getId()) != item);"));
        assertFalse(source.contains(".getRoom(this.getRoomId()) .getHabboItem(item.getId())"));
    }
}
