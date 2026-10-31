package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Raising an LTD stack generated numbers after the new stack, leaving a gap after the old one. */
class LimitedStackIncreaseContractTest {

    @Test
    void theNewNumbersFollowTheLastOneMade() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"));
        String method = source.substring(source.indexOf("public CatalogLimitedConfiguration createOrUpdateLimitedConfig("));

        assertTrue(method.contains("limitedConfiguration.getTotalSet() + 1,"));
        assertFalse(method.contains("item.limitedStack + 1, item.limitedStack - limitedConfiguration.getTotalSet()"));
    }
}
