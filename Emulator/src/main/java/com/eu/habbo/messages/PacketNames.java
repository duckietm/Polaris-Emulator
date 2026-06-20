package com.eu.habbo.messages;

import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

public class PacketNames {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketNames.class);

    private final HashMap<Integer, String> incoming;
    private final HashMap<Integer, String> outgoing;

    public PacketNames() {
        this.incoming = new HashMap<>();
        this.outgoing = new HashMap<>();
    }

    public void initialize() {
        RuntimeValidationReport report = new RuntimeValidationReport();
        report.merge(PacketRuntimeValidator.validatePacketNameClass("Incoming", Incoming.class));
        report.merge(PacketRuntimeValidator.validatePacketNameClass("Outgoing", Outgoing.class));
        report.logErrors(LOGGER, "Packet name validation");

        PacketNames.getNames(Incoming.class, this.incoming);
        PacketNames.getNames(Outgoing.class, this.outgoing);
    }

    public String getIncomingName(int key) {
        return this.incoming.getOrDefault(key, "Unknown");
    }

    public String getOutgoingName(int key) {
        return this.outgoing.getOrDefault(key, "Unknown");
    }

    private static void getNames(Class<?> clazz, HashMap<Integer, String> target) {
        for (Field field : clazz.getFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType() == int.class) {
                try {
                    int packetId = field.getInt(null);
                    if (packetId > 0) {
                        if (target.containsKey(packetId)) {
                            LOGGER.warn("Duplicate packet id found {} for {}.", packetId, clazz.getSimpleName());
                            continue;
                        }

                        target.put(packetId, field.getName());
                    }
                } catch (IllegalAccessException e) {
                    LOGGER.error("Failed to read field integer.", e);
                }
            }
        }
    }

}
