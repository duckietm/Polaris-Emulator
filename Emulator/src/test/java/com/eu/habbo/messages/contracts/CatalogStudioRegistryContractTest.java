package com.eu.habbo.messages.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogStudioRegistryContractTest {

    @Test
    void allStudioHeadersAreActiveInBothDirections() throws Exception {
        JavaPacketRegistry registry = JavaPacketRegistry.discover(Path.of("src/main/java"));

        int[] clientHeaders = {10067, 10071, 10072, 10073, 10078, 10079, 10080, 10081, 10082};
        for (int header : clientHeaders) {
            assertEquals(
                    header,
                    registry.require(JavaPacketRegistry.Direction.CLIENT_TO_SERVER, header)
                            .header());
        }
        int[] serverHeaders = {10067, 10071, 10072, 10073, 10078, 10081, 10082};
        for (int header : serverHeaders) {
            assertEquals(
                    header,
                    registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, header)
                            .header());
        }
    }
}
