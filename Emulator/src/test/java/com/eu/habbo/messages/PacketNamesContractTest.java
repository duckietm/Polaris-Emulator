package com.eu.habbo.messages;

import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketNamesContractTest {
    @Test
    void incomingPacketNameIdsAreUnique() throws Exception {
        assertPublicFinalPacketIdsAreUnique(Incoming.class);
    }

    @Test
    void outgoingPacketNameIdsAreUnique() throws Exception {
        assertPublicFinalPacketIdsAreUnique(Outgoing.class);
    }

    private static void assertPublicFinalPacketIdsAreUnique(Class<?> packetClass) throws Exception {
        Map<Integer, String> seen = new HashMap<>();
        Map<Integer, String> duplicates = new HashMap<>();

        for (Field field : packetClass.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers)
                    || !Modifier.isStatic(modifiers)
                    || !Modifier.isFinal(modifiers)
                    || field.getType() != int.class) {
                continue;
            }

            int packetId = field.getInt(null);
            if (packetId <= 0) {
                continue;
            }

            String previous = seen.putIfAbsent(packetId, field.getName());
            if (previous != null) {
                duplicates.put(packetId, previous + " / " + field.getName());
            }
        }

        assertTrue(duplicates.isEmpty(), packetClass.getSimpleName() + " has duplicate packet IDs: " + duplicates);
    }
}
