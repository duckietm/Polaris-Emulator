package com.eu.habbo.messages;

import com.eu.habbo.messages.incoming.MessageHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRuntimeValidatorTest {
    @Test
    void reportsDuplicatePacketNamesWithBothAliases() {
        RuntimeValidationReport report = PacketRuntimeValidator.validatePacketNameClass("Incoming", DuplicateIds.class);

        assertTrue(report.hasErrors());
        assertEquals(1, report.errors().size());
        assertTrue(report.errors().get(0).message().contains("Duplicate Incoming packet id 100"));
        assertTrue(report.errors().get(0).message().contains("FIRST"));
        assertTrue(report.errors().get(0).message().contains("SECOND"));
    }

    @Test
    void reportsHandlersThatCannotBeInstantiatedAtStartup() {
        RuntimeValidationReport report = PacketRuntimeValidator.validateHandlers(Map.of(
                1, ValidHandler.class,
                2, MissingDefaultConstructorHandler.class
        ));

        assertTrue(report.hasErrors());
        assertEquals(1, report.errors().size());
        assertTrue(report.errors().get(0).message().contains("2"));
        assertTrue(report.errors().get(0).message().contains(MissingDefaultConstructorHandler.class.getName()));
    }

    @Test
    void acceptsUniquePacketNamesAndDefaultConstructibleHandlers() {
        RuntimeValidationReport names = PacketRuntimeValidator.validatePacketNameClass("Outgoing", UniqueIds.class);
        RuntimeValidationReport handlers = PacketRuntimeValidator.validateHandlers(Map.of(1, ValidHandler.class));

        assertFalse(names.hasErrors());
        assertFalse(handlers.hasErrors());
    }

    public static final class DuplicateIds {
        public static final int FIRST = 100;
        public static final int SECOND = 100;
    }

    public static final class UniqueIds {
        public static final int FIRST = 100;
        public static final int SECOND = 101;
    }

    public static final class ValidHandler extends MessageHandler {
        @Override
        public void handle() {
        }
    }

    public static final class MissingDefaultConstructorHandler extends MessageHandler {
        public MissingDefaultConstructorHandler(String value) {
        }

        @Override
        public void handle() {
        }
    }
}
