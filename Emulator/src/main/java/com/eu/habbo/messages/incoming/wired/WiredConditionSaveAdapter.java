package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.messages.ClientMessage;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Supplier;

/** Deterministic typed/legacy condition-save compatibility boundary. */
final class WiredConditionSaveAdapter {
    private enum SaveMode {
        TYPED_SETTINGS,
        LEGACY_PACKET
    }

    private static final ClassValue<SaveMode> SAVE_MODES = new ClassValue<>() {
        @Override
        protected SaveMode computeValue(Class<?> conditionType) {
            try {
                Method typedMethod = conditionType.getMethod("saveData", WiredSettings.class);
                return Modifier.isAbstract(typedMethod.getModifiers())
                        ? SaveMode.LEGACY_PACKET
                        : SaveMode.TYPED_SETTINGS;
            } catch (NoSuchMethodException impossible) {
                throw new IllegalStateException(
                        "Condition type has no typed save descriptor: " + conditionType.getName(), impossible);
            }
        }
    };

    boolean save(InteractionWiredCondition condition, ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        return save(condition, () -> InteractionWired.readSettings(packet, false), () -> packet);
    }

    boolean save(
            InteractionWiredCondition condition,
            Supplier<WiredSettings> typedSettings,
            Supplier<ClientMessage> legacyPacket) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(typedSettings, "typedSettings");
        Objects.requireNonNull(legacyPacket, "legacyPacket");

        if (SAVE_MODES.get(condition.getClass()) == SaveMode.LEGACY_PACKET) {
            return condition.saveData(legacyPacket.get());
        }

        return condition.saveData(typedSettings.get());
    }
}
