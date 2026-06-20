package com.eu.habbo.messages;

import com.eu.habbo.messages.incoming.MessageHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public final class PacketRuntimeValidator {

    private PacketRuntimeValidator() {
    }

    public static RuntimeValidationReport validatePacketNameClass(String direction, Class<?> packetClass) {
        RuntimeValidationReport report = new RuntimeValidationReport();
        Map<Integer, String> packetNames = new HashMap<>();

        for (Field field : packetClass.getFields()) {
            int modifiers = field.getModifiers();

            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || field.getType() != int.class) {
                continue;
            }

            try {
                int packetId = field.getInt(null);

                if (packetId <= 0) {
                    continue;
                }

                String existingName = packetNames.putIfAbsent(packetId, field.getName());

                if (existingName != null) {
                    report.addError("Duplicate " + direction + " packet id " + packetId + " for " + existingName + " and " + field.getName());
                }
            } catch (IllegalAccessException e) {
                report.addError("Unable to read " + direction + " packet id field " + packetClass.getName() + "." + field.getName());
            }
        }

        return report;
    }

    public static RuntimeValidationReport validateHandlers(Map<Integer, Class<? extends MessageHandler>> handlers) {
        RuntimeValidationReport report = new RuntimeValidationReport();

        for (Map.Entry<Integer, Class<? extends MessageHandler>> entry : handlers.entrySet()) {
            Integer packetId = entry.getKey();
            Class<? extends MessageHandler> handlerClass = entry.getValue();

            if (packetId == null || packetId < 0) {
                report.addError("Incoming handler " + handlerClass + " is registered with invalid packet id " + packetId);
                continue;
            }

            if (handlerClass == null) {
                report.addError("Incoming packet id " + packetId + " is registered with a null handler");
                continue;
            }

            try {
                handlerClass.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                report.addError("Incoming packet id " + packetId + " uses " + handlerClass.getName() + " without a no-argument constructor");
            } catch (NoClassDefFoundError e) {
                report.addError("Incoming packet id " + packetId + " uses " + handlerClass.getName() + " but dependency " + e.getMessage() + " is missing");
            } catch (LinkageError e) {
                report.addError("Incoming packet id " + packetId + " uses " + handlerClass.getName() + " but linkage failed: " + e.getMessage());
            }
        }

        return report;
    }
}
