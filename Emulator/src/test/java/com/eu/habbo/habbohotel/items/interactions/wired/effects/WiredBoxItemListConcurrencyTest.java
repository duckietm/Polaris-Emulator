package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The selected items of these boxes lived in a plain LinkedHashSet, HashSet or ArrayList that the
 * packet thread changed (saveData, opening the box), the stack tick changed while executing
 * (removeIf on deleted items) and the persistence thread read (getWiredData): opening or saving a
 * box while its stack ran threw ConcurrentModificationException and aborted the stack, or persisted
 * a half-written snapshot. Each now holds a copy-on-write collection: small, read far more than
 * written, ordered like before, and safe to iterate while it changes.
 */
class WiredBoxItemListConcurrencyTest {

    private static final Path EFFECTS =
            Path.of("src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects");

    private static final List<String> BOXES = List.of(
            "WiredEffectTriggerStacks.java",
            "WiredEffectSetAltitude.java",
            "WiredEffectMoveFurniTowards.java",
            "WiredEffectMoveFurniAway.java",
            "WiredEffectToggleFurni.java",
            "WiredEffectBotTeleport.java",
            "WiredEffectOverrideHeight.java");

    private static final Pattern PLAIN_ITEMS = Pattern.compile(
            "(this\\.items|private[^;=]*\\bitems)\\s*=\\s*new (LinkedHashSet|HashSet|ArrayList)<>\\(\\)");

    @Test
    void everyBoxHoldsItsItemsInACopyOnWriteCollection() throws Exception {
        for (String box : BOXES) {
            String source = Files.readString(EFFECTS.resolve(box));

            assertFalse(PLAIN_ITEMS.matcher(source).find(), box + " still keeps its items in a plain collection");
            assertTrue(
                    source.contains("new CopyOnWriteArraySet<>()") || source.contains("new CopyOnWriteArrayList<>()"),
                    box + " holds its items in a copy-on-write collection");
        }
    }
}
