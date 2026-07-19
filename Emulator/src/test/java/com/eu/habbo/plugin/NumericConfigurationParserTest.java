package com.eu.habbo.plugin;

import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericConfigurationParserTest {

    @Test
    void parsesSemicolonDelimitedIntegerArray() {
        assertArrayEquals(
                new int[]{40, 99},
                NumericConfigurationParser.parseIntArray(
                        "40;99",
                        ";",
                        new int[]{1},
                        "discount.additional.thresholds"
                )
        );
    }

    @Test
    void parsesCommaDelimitedIntegerList() {
        assertEquals(
                List.of(0, 2, 8),
                NumericConfigurationParser.parseIntList(
                        "0,2,8",
                        ",",
                        List.of(1),
                        "hotel.gifts.box_types"
                )
        );
    }

    @Test
    void retainsCurrentArrayWhenAConfiguredValueIsMalformed() {
        int[] currentValue = {40, 99};

        assertSame(
                currentValue,
                NumericConfigurationParser.parseIntArray(
                        "40;invalid",
                        ";",
                        currentValue,
                        "discount.additional.thresholds"
                )
        );
    }

    @Test
    void retainsCurrentListWhenAConfiguredValueIsMalformed() {
        List<Integer> currentValue = List.of(0, 2, 8);

        assertSame(
                currentValue,
                NumericConfigurationParser.parseIntList(
                        "0,invalid,8",
                        ",",
                        currentValue,
                        "hotel.gifts.box_types"
                )
        );
    }

    @Test
    void bannedBubbleSnapshotIsBuiltBeforePublication() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/plugin/PluginManager.java"
        ));

        assertTrue(source.contains(
                "int[] bannedBubbleSnapshot = NumericConfigurationParser.parseIntArray("
        ));
        assertTrue(source.contains(
                "RoomChatMessage.BANNED_BUBBLES = bannedBubbleSnapshot;"
        ));
        assertFalse(source.contains("RoomChatMessage.BANNED_BUBBLES[i]"));
    }

    @Test
    void numericConfigurationSnapshotsArePublishedThroughVolatileFields() throws NoSuchFieldException {
        assertTrue(Modifier.isVolatile(
                RoomChatMessage.class.getField("BANNED_BUBBLES").getModifiers()
        ));
        assertTrue(Modifier.isVolatile(
                DiscountComposer.class.getField("ADDITIONAL_DISCOUNT_THRESHOLDS").getModifiers()
        ));
        assertTrue(Modifier.isVolatile(
                GiftConfigurationComposer.class.getField("BOX_TYPES").getModifiers()
        ));
        assertTrue(Modifier.isVolatile(
                GiftConfigurationComposer.class.getField("RIBBON_TYPES").getModifiers()
        ));
    }
}
