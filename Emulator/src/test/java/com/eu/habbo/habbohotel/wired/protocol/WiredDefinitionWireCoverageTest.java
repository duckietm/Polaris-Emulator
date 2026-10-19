package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.WiredInteractionRegistryFixture;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/**
 * Every registered wired box's definition converts to the official layout: the legacy fields parse
 * with nothing unexpected left over, and the result reads back exactly as the official client reads it.
 */
class WiredDefinitionWireCoverageTest {
    /** A legacy condition ends with two unused ints; a legacy trigger sometimes with one. */
    private static final Set<Integer> TRIGGER_LEFTOVERS = Set.of(0, 4);

    @Test
    void everyRegisteredBoxConvertsToTheOfficialLayout() throws Exception {
        List<String> failures = new ArrayList<>();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));

            for (Class<? extends InteractionWired> type : WiredInteractionRegistryFixture.wiredTypes().stream()
                    .sorted(Comparator.comparing(Class::getName))
                    .toList()) {
                try {
                    InteractionWired item = WiredInteractionRegistryFixture.instantiate(type);
                    WiredDefinitionWire.Holder holder = WiredHolders.of(item);
                    ServerMessage legacy = new ServerMessage(1);
                    item.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));

                    ByteBuf body = legacy.get();
                    body.skipBytes(6);
                    WiredDefinitionWire.Definition definition = WiredDefinitionWire.readLegacy(body, holder);
                    body.release();

                    boolean expectedLeftover =
                            switch (holder) {
                                case ACTION, ADDON, SELECTOR, VARIABLE -> definition.leftover == 0;
                                case CONDITION -> definition.leftover == 8;
                                case TRIGGER -> TRIGGER_LEFTOVERS.contains(definition.leftover);
                            };
                    if (!expectedLeftover) failures.add(type.getSimpleName() + " left " + definition.leftover);

                    ServerMessage official = new ServerMessage(2);
                    WiredDefinitionWire.writeOfficial(definition, holder, official);
                    ByteBuf written = official.get();
                    written.skipBytes(6);
                    OfficialReader.read(written, holder);
                    if (written.isReadable()) failures.add(type.getSimpleName() + " official trailing bytes");
                    written.release();
                } catch (Throwable failure) {
                    failures.add(type.getSimpleName() + " " + WiredInteractionRegistryFixture.rootCause(failure));
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void boxesGoToTheOfficialHolderTheyBelongTo() throws Exception {
        java.util.Map<WiredDefinitionWire.Holder, Integer> counts =
                new java.util.EnumMap<>(WiredDefinitionWire.Holder.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));

            for (Class<? extends InteractionWired> type : WiredInteractionRegistryFixture.wiredTypes()) {
                InteractionWired item = WiredInteractionRegistryFixture.instantiate(type);
                counts.merge(WiredHolders.of(item), 1, Integer::sum);
            }
        }

        for (WiredDefinitionWire.Holder holder : WiredDefinitionWire.Holder.values()) {
            assertTrue(counts.getOrDefault(holder, 0) > 0, "no box uses " + holder + ": " + counts);
        }
        assertEquals(10, counts.get(WiredDefinitionWire.Holder.VARIABLE), counts.toString());
    }

    @Test
    void everySelectorSendsItsOwnOfficialSelectorCode() throws Exception {
        java.util.Map<Integer, String> seen = new java.util.HashMap<>();
        List<String> failures = new ArrayList<>();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));

            for (Class<? extends InteractionWired> type : WiredInteractionRegistryFixture.wiredTypes()) {
                InteractionWired item = WiredInteractionRegistryFixture.instantiate(type);
                if (WiredHolders.of(item) != WiredDefinitionWire.Holder.SELECTOR) continue;

                ServerMessage legacy = new ServerMessage(1);
                item.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
                ServerMessage official = new ServerMessage(2);
                WiredDefinitionWire.writeOfficial(legacy, WiredDefinitionWire.Holder.SELECTOR, official);

                ByteBuf in = official.get();
                in.skipBytes(6);
                in.readInt();
                in.skipBytes(in.readInt() * 4);
                in.skipBytes(in.readInt() * 4);
                in.readInt();
                in.readInt();
                OfficialReader.string(in);
                in.skipBytes(in.readInt() * 4);
                int variables = in.readInt();
                for (int i = 0; i < variables; i++) OfficialReader.string(in);
                in.skipBytes(in.readInt() * 4);
                in.skipBytes(in.readInt() * 4);
                int code = in.readInt();
                in.release();

                boolean official019 = code >= 0 && code <= 19;
                if (!official019) {
                    failures.add(type.getSimpleName() + " sends selector code " + code);
                }
                String previous = seen.put(code, type.getSimpleName());
                if (previous != null && !previous.equals(type.getSimpleName())) {
                    failures.add(type.getSimpleName() + " shares code " + code + " with " + previous);
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
        for (int code = 0; code <= 19; code++) {
            assertTrue(seen.containsKey(code), "no selector sends official code " + code + ": " + seen);
        }
    }

    @Test
    void everyAddonAndVariableHasItsOwnOfficialCode() throws Exception {
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        List<String> failures = new ArrayList<>();
        int variables = 0;

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));

            for (Class<? extends InteractionWired> type : WiredInteractionRegistryFixture.wiredTypes()) {
                InteractionWired item = WiredInteractionRegistryFixture.instantiate(type);
                WiredDefinitionWire.Holder holder = WiredHolders.of(item);
                if (holder != WiredDefinitionWire.Holder.ADDON && holder != WiredDefinitionWire.Holder.VARIABLE)
                    continue;

                ServerMessage legacy = new ServerMessage(1);
                item.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
                ByteBuf body = legacy.get();
                body.skipBytes(6);
                int code = WiredDefinitionWire.officialCode(holder, WiredDefinitionWire.readLegacy(body, holder).code);
                body.release();

                if (code < 0) failures.add(type.getSimpleName() + " has no official " + holder + " code");
                if (holder == WiredDefinitionWire.Holder.VARIABLE) {
                    variables++;
                    if (code > 9) failures.add(type.getSimpleName() + " sends variable code " + code);
                }
                String previous = seen.put(holder + ":" + code, type.getSimpleName());
                if (previous != null && !previous.equals(type.getSimpleName())) {
                    failures.add(type.getSimpleName() + " shares " + holder + " code " + code + " with " + previous);
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertEquals(10, variables);
    }

    @Test
    void actionsGoOutWithOfficialCodesOrOursFrom1000() throws Exception {
        java.util.Map<Integer, Integer> legacyByOfficial = new java.util.HashMap<>();
        List<String> failures = new ArrayList<>();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));

            for (Class<? extends InteractionWired> type : WiredInteractionRegistryFixture.wiredTypes()) {
                InteractionWired item = WiredInteractionRegistryFixture.instantiate(type);
                if (WiredHolders.of(item) != WiredDefinitionWire.Holder.ACTION) continue;

                ServerMessage legacy = new ServerMessage(1);
                item.serializeWiredData(legacy, mock(Room.class, Answers.RETURNS_DEEP_STUBS));
                ByteBuf body = legacy.get();
                body.skipBytes(6);
                int legacyCode = WiredDefinitionWire.readLegacy(body, WiredDefinitionWire.Holder.ACTION).code;
                body.release();
                int code = WiredDefinitionWire.officialCode(WiredDefinitionWire.Holder.ACTION, legacyCode);

                if (code < 0 || (code > 64 && code < WiredActionCodes.FIRST_CUSTOM_ACTION)) {
                    failures.add(type.getSimpleName() + " sends action code " + code);
                }
                Integer previous = legacyByOfficial.put(code, legacyCode);
                if (previous != null && previous != legacyCode) {
                    failures.add("action code " + code + " is used by legacy codes " + previous + " and " + legacyCode);
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertEquals(28, WiredDefinitionWire.officialCode(WiredDefinitionWire.Holder.ACTION, 41), "control clock");
        assertEquals(
                1126, WiredDefinitionWire.officialCode(WiredDefinitionWire.Holder.ACTION, 126), "ours: modify array");
    }

    @Test
    void theOfficialLayoutKeepsEveryLegacyField() {
        WiredDefinitionWire.Definition definition = new WiredDefinitionWire.Definition();
        definition.furniLimit = 20;
        definition.stuffIds = new int[] {7, 8};
        definition.spriteId = 99;
        definition.id = 1234;
        definition.stringParam = "hi";
        definition.intParams = new int[] {1, 2, 3};
        definition.code = 41; // control clock, officially 28
        definition.delay = 6;

        ServerMessage official = new ServerMessage(2);
        WiredDefinitionWire.writeOfficial(definition, WiredDefinitionWire.Holder.ACTION, official);
        ByteBuf in = official.get();
        in.skipBytes(6);

        assertEquals(20, in.readInt());
        assertEquals(2, in.readInt());
        assertEquals(7, in.readInt());
        assertEquals(8, in.readInt());
        assertEquals(0, in.readInt(), "stuffIds2");
        assertEquals(99, in.readInt());
        assertEquals(1234, in.readInt());
        assertEquals("hi", OfficialReader.string(in));
        assertEquals(3, in.readInt());
        in.skipBytes(12);
        assertEquals(0, in.readInt(), "variableIds");
        assertEquals(0, in.readInt(), "furni sources");
        assertEquals(0, in.readInt(), "user sources");
        assertEquals(28, in.readInt(), "code");
        assertEquals(6, in.readInt(), "delay");
        assertFalse(in.readBoolean(), "advanced mode");
        in.release();
    }

    /** The official client's read order (AS3 §_-lk§ and its holder subclasses). */
    static final class OfficialReader {
        static void read(ByteBuf in, WiredDefinitionWire.Holder holder) {
            in.readInt();
            ints(in);
            ints(in);
            in.readInt();
            in.readInt();
            string(in);
            ints(in);
            int variables = in.readInt();
            for (int i = 0; i < variables; i++) string(in);
            ints(in);
            ints(in);
            in.readInt();
            switch (holder) {
                case ACTION, CONDITION -> in.readInt();
                case SELECTOR -> {
                    in.readBoolean();
                    in.readBoolean();
                }
                default -> {}
            }
            in.readBoolean();
            for (int slot = 0; slot < 2; slot++) {
                int lists = in.readInt();
                for (int i = 0; i < lists; i++) ints(in);
            }
            ints(in);
            ints(in);
            in.readBoolean();
            if (holder == WiredDefinitionWire.Holder.CONDITION) {
                in.readByte();
                in.readBoolean();
            }
            contexts(in);
            ints(in);
        }

        /** Only the variable leaderboard context (type 8) is sent so far. */
        private static void contexts(ByteBuf in) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int type = in.readInt();
                if (type != 8) throw new IllegalStateException("context type " + type);
                in.readInt();
                in.readInt();
                in.readInt();
                in.readInt();
                int variables = in.readInt();
                for (int v = 0; v < variables; v++) {
                    string(in);
                    string(in);
                    in.readBoolean();
                    in.readBoolean();
                    in.readInt();
                }
            }
        }

        private static void ints(ByteBuf in) {
            int count = in.readInt();
            in.skipBytes(count * 4);
        }

        static String string(ByteBuf in) {
            byte[] bytes = new byte[in.readUnsignedShort()];
            in.readBytes(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
