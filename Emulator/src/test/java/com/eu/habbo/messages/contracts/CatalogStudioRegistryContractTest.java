package com.eu.habbo.messages.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogStudioRegistryContractTest {

    @Test
    void allStudioHeadersAreActiveInBothDirections() throws Exception {
        JavaPacketRegistry registry = JavaPacketRegistry.discover(Path.of("src/main/java"));

        for (int header = 10067; header <= 10076; header++) {
            assertEquals(
                    header,
                    registry.require(JavaPacketRegistry.Direction.CLIENT_TO_SERVER, header)
                            .header());
            assertEquals(
                    header,
                    registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, header)
                            .header());
        }
        for (int header = 10077; header <= 10080; header++) {
            assertEquals(
                    header,
                    registry.require(JavaPacketRegistry.Direction.CLIENT_TO_SERVER, header)
                            .header());
        }
        for (int header = 10077; header <= 10078; header++) {
            assertEquals(
                    header,
                    registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, header)
                            .header());
        }
        assertEquals(
                10081,
                registry.require(JavaPacketRegistry.Direction.CLIENT_TO_SERVER, 10081)
                        .header());
        assertEquals(
                10081,
                registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, 10081)
                        .header());
        assertEquals(
                10082,
                registry.require(JavaPacketRegistry.Direction.CLIENT_TO_SERVER, 10082)
                        .header());
        assertEquals(
                10082,
                registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, 10082)
                        .header());
    }
}
