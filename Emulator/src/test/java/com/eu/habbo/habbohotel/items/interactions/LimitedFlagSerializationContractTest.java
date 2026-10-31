package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A limited gift or highscore board wrote its stuff data format without the limited flag, then HabboItem appended
 * the LTD numbers anyway: the client read them as the next furni's fields.
 */
class LimitedFlagSerializationContractTest {

    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/items/interactions/" + name));
    }

    @Test
    void theFormatCarriesTheLimitedFlagWhenTheNumbersFollow() throws Exception {
        assertTrue(
                source("InteractionGift.java").contains("serverMessage.appendInt(1 | (this.isLimited() ? 256 : 0));"));
        assertTrue(source("InteractionWiredHighscore.java")
                .contains("serverMessage.appendInt(6 | (this.isLimited() ? 256 : 0));"));
    }
}
