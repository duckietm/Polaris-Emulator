package com.eu.habbo.messages.contracts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPacketRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversTheRealEmulatorRegistry() throws IOException {
        JavaPacketRegistry registry = JavaPacketRegistry.discover(Path.of("src/main/java"));

        assertTrue(registry.active().size() > 800);
        assertTrue(registry.active().stream().anyMatch(packet -> packet.header() == 412));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, 84));
        assertTrue(registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, 1327)
                .source().endsWith("RoomRemoveRightsListComposer.java"));
    }

    @Test
    void discoversRegisteredIncomingHandlersAndOutgoingComposers() throws IOException {
        Path root = fixtureRoot();
        write(root, "com/eu/habbo/messages/incoming/Incoming.java", """
                package com.eu.habbo.messages.incoming;
                public class Incoming {
                    public static final int OpenEvent = 100;
                    public static final int DeclaredOnlyEvent = 101;
                }
                """);
        write(root, "com/eu/habbo/messages/outgoing/Outgoing.java", """
                package com.eu.habbo.messages.outgoing;
                public class Outgoing {
                    public static final int OpenComposer = 200;
                }
                """);
        write(root, "com/eu/habbo/messages/PacketManager.java", """
                package com.eu.habbo.messages;
                class PacketManager {
                    void register() { registerHandler(Incoming.OpenEvent, OpenEvent.class); }
                }
                """);
        Path handler = write(root, "com/eu/habbo/messages/incoming/test/OpenEvent.java",
                "package com.eu.habbo.messages.incoming.test; class OpenEvent {}\n");
        Path composer = write(root, "com/eu/habbo/messages/outgoing/test/OpenComposer.java", """
                package com.eu.habbo.messages.outgoing.test;
                class OpenComposer {
                    void compose() { response.init(Outgoing.OpenComposer); }
                }
                """);

        JavaPacketRegistry registry = JavaPacketRegistry.discover(root);

        assertEquals(handler, registry.require(JavaPacketRegistry.Direction.CLIENT_TO_SERVER, 100).source());
        assertEquals(composer, registry.require(JavaPacketRegistry.Direction.SERVER_TO_CLIENT, 200).source());
        assertTrue(registry.declaredOnly().stream().anyMatch(packet -> packet.header() == 101));
    }

    @Test
    void rejectsDuplicateActiveHeaders() throws IOException {
        Path root = fixtureRoot();
        write(root, "com/eu/habbo/messages/incoming/Incoming.java", """
                package com.eu.habbo.messages.incoming;
                public class Incoming {
                    public static final int FirstEvent = 100;
                    public static final int SecondEvent = 100;
                }
                """);
        write(root, "com/eu/habbo/messages/outgoing/Outgoing.java",
                "package com.eu.habbo.messages.outgoing; public class Outgoing {}\n");
        write(root, "com/eu/habbo/messages/PacketManager.java", """
                package com.eu.habbo.messages;
                class PacketManager { void register() {
                    registerHandler(Incoming.FirstEvent, FirstEvent.class);
                    registerHandler(Incoming.SecondEvent, SecondEvent.class);
                }}
                """);
        write(root, "com/eu/habbo/messages/incoming/test/FirstEvent.java",
                "package com.eu.habbo.messages.incoming.test; class FirstEvent {}\n");
        write(root, "com/eu/habbo/messages/incoming/test/SecondEvent.java",
                "package com.eu.habbo.messages.incoming.test; class SecondEvent {}\n");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JavaPacketRegistry.discover(root));

        assertTrue(error.getMessage().contains("duplicate active client_to_server header 100"));
        assertTrue(error.getMessage().contains("FirstEvent"));
        assertTrue(error.getMessage().contains("SecondEvent"));
    }

    @Test
    void rejectsRegistrationsWhoseSourceIsMissing() throws IOException {
        Path root = fixtureRoot();
        write(root, "com/eu/habbo/messages/incoming/Incoming.java", """
                package com.eu.habbo.messages.incoming;
                public class Incoming { public static final int MissingEvent = 100; }
                """);
        write(root, "com/eu/habbo/messages/outgoing/Outgoing.java",
                "package com.eu.habbo.messages.outgoing; public class Outgoing {}\n");
        write(root, "com/eu/habbo/messages/PacketManager.java", """
                package com.eu.habbo.messages;
                class PacketManager { void register() {
                    registerHandler(Incoming.MissingEvent, MissingEvent.class);
                }}
                """);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JavaPacketRegistry.discover(root));

        assertTrue(error.getMessage().contains("source for MissingEvent is missing"));
    }

    private Path fixtureRoot() throws IOException {
        Path root = tempDir.resolve("src/main/java");
        Files.createDirectories(root);
        return root;
    }

    private static Path write(Path root, String relative, String contents) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
        return path;
    }
}
