package com.eu.habbo.messages.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.messages.incoming.UnsupportedIncoming;
import com.eu.habbo.messages.outgoing.UnsupportedOutgoing;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PacketRegistryPolicyContractTest {
    private final Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();

    @Test
    void keepsUnsupportedJavaHeadersSynchronizedWithTheSharedPolicy() throws Exception {
        PacketContractManifest manifest =
                new PacketContractManifestLoader().load(repositoryRoot.resolve("protocol/packet-field-contracts.json"));
        Set<String> expected = manifest.registry().unsupported().stream()
                .filter(packet -> packet.side().equals("java"))
                .map(packet -> packet.direction() + ':' + packet.symbol())
                .collect(Collectors.toSet());
        Set<String> actual = Stream.concat(
                        unsupported("client_to_server", UnsupportedIncoming.class),
                        unsupported("server_to_client", UnsupportedOutgoing.class))
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    private static Stream<String> unsupported(String direction, Class<?> registry) {
        return Stream.of(registry.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .peek(PacketRegistryPolicyContractTest::requireUnsupportedValue)
                .map(Field::getName)
                .map(symbol -> direction + ':' + symbol);
    }

    private static void requireUnsupportedValue(Field field) {
        try {
            if (field.getInt(null) != -1) {
                throw new AssertionError(field.getDeclaringClass().getSimpleName() + '.' + field.getName()
                        + " must remain an unsupported -1 placeholder");
            }
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Cannot read unsupported packet header " + field.getName(), exception);
        }
    }
}
