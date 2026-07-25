package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;
import com.eu.habbo.messages.outgoing.wired.WiredFurniRuntimeStateComposer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Freezes the interaction-key, editor-layout and packet-header contracts used by existing wired
 * furniture, clients and plugins. Every persisted interaction key must have exactly one canonical
 * source registration.
 *
 * <p>Regeneration is an explicit compatibility review action:
 *
 * <pre>
 * mvn test -Dtest=WiredInteractionProtocolContractTest \
 *   -Dpolaris.wired.protocol.regenerate=true
 * </pre>
 */
class WiredInteractionProtocolContractTest {

    private static final String REGENERATE_PROPERTY = "polaris.wired.protocol.regenerate";
    private static final Path CONTRACT =
            Path.of("src", "test", "resources", "wired-compatibility", "interaction-protocol-v1.txt");
    private static final Path ITEM_MANAGER =
            Path.of("src", "main", "java", "com", "eu", "habbo", "habbohotel", "items", "ItemManager.java");
    private static final Pattern INTERACTION = Pattern.compile(
            "new\\s+ItemInteraction\\(\\s*\"([^\"]+)\"\\s*,\\s*([A-Za-z0-9_$.]+)\\.class\\s*\\)", Pattern.MULTILINE);

    private static final List<String> INCOMING_HEADERS = List.of(
            "WiredTriggerSaveDataEvent",
            "WiredEffectSaveDataEvent",
            "WiredConditionSaveDataEvent",
            "WiredApplySetConditionsEvent",
            "WiredMonitorRequestEvent",
            "WiredRoomSettingsRequestEvent",
            "WiredRoomSettingsSaveEvent",
            "WiredUserVariablesRequestEvent",
            "WiredUserVariableUpdateEvent",
            "WiredUserVariableManageEvent",
            "WiredUserInspectMoveEvent",
            "WiredFurniRuntimeStateRequestEvent",
            "WiredFeatureCapabilitiesEvent");

    private static final List<String> OUTGOING_HEADERS = List.of(
            "WiredTriggerDataComposer",
            "WiredEffectDataComposer",
            "WiredConditionDataComposer",
            "WiredOpenComposer",
            "WiredSavedComposer",
            "WiredRewardAlertComposer",
            "WiredMovementsComposer",
            "WiredMonitorDataComposer",
            "WiredRoomSettingsDataComposer",
            "WiredUserVariablesDataComposer");

    @Test
    void interactionLayoutAndHeaderContractsStayStable() throws Exception {
        List<String> actual = snapshot();
        if (System.getProperty(REGENERATE_PROPERTY) != null) {
            Files.createDirectories(CONTRACT.getParent());
            Files.write(CONTRACT, actual, StandardCharsets.UTF_8);
            return;
        }

        assertTrue(
                Files.isRegularFile(CONTRACT),
                "Missing wired protocol contract; regenerate with -D" + REGENERATE_PROPERTY + "=true");
        assertEquals(
                Files.readAllLines(CONTRACT, StandardCharsets.UTF_8),
                actual,
                "A persisted interaction key, wired layout code or packet header changed. Restore the old "
                        + "contract or review and regenerate this fixture as an explicit compatibility change.");
    }

    @Test
    void sourceRegistrationsHaveNoDuplicateInteractionKeys() throws Exception {
        assertTrue(duplicateRegistrations().isEmpty(), "Every built-in interaction key must be registered once");
    }

    @Test
    void defaultRegistryContainsEveryFrozenWiredRegistration() throws Exception {
        TestItemManager manager = new TestItemManager();
        manager.loadDefaults();

        List<String> expectedNames = registrations().stream()
                .map(InteractionRegistration::key)
                .distinct()
                .sorted()
                .toList();
        List<String> actualNames = manager.getInteractionList().stream()
                .filter(name -> name.startsWith("wf_"))
                .toList();

        assertEquals(expectedNames, actualNames);
    }

    private static List<String> snapshot() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# Polaris wired interaction and protocol contract v1");
        lines.add("# Interaction registrations preserve source order and intentional aliases.");
        lines.add("# Every interaction key has one canonical source registration.");
        lines.add("");

        List<InteractionRegistration> registrations = registrations();
        for (int index = 0; index < registrations.size(); index++) {
            InteractionRegistration registration = registrations.get(index);
            lines.add("INTERACTION " + String.format("%03d", index) + " " + registration.key() + "="
                    + registration.type());
        }

        lines.add("");
        appendEnum(lines, "EFFECT", WiredEffectType.values(), type -> type.code);
        appendEnum(lines, "TRIGGER", WiredTriggerType.values(), type -> type.code);
        appendEnum(lines, "CONDITION", WiredConditionType.values(), type -> type.code);

        lines.add("");
        appendHeaders(lines, "INCOMING", Incoming.class, INCOMING_HEADERS);
        appendHeaders(lines, "OUTGOING", Outgoing.class, OUTGOING_HEADERS);
        appendHeader(lines, "OUTGOING", "WiredFurniRuntimeStateComposer", WiredFurniRuntimeStateComposer.class);
        appendHeader(lines, "OUTGOING", "WiredFurniOpacityComposer", WiredFurniOpacityComposer.class);
        return lines;
    }

    private static <E extends Enum<E>> void appendEnum(
            List<String> lines, String family, E[] constants, ToIntFunction<E> code) {
        for (int ordinal = 0; ordinal < constants.length; ordinal++) {
            E constant = constants[ordinal];
            lines.add("LAYOUT " + family + " " + String.format("%03d", ordinal) + " " + constant.name() + "="
                    + code.applyAsInt(constant));
        }
    }

    private static void appendHeaders(List<String> lines, String direction, Class<?> constantsClass, List<String> names)
            throws ReflectiveOperationException {
        for (String name : names) {
            Field field = constantsClass.getField(name);
            lines.add("HEADER " + direction + " " + name + "=" + field.getInt(null));
        }
    }

    private static void appendHeader(List<String> lines, String direction, String name, Class<?> composerClass)
            throws ReflectiveOperationException {
        Field field = composerClass.getDeclaredField("HEADER");
        field.setAccessible(true);
        lines.add("HEADER " + direction + " " + name + "=" + field.getInt(null));
    }

    private static Map<String, List<String>> duplicateRegistrations() throws Exception {
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        for (InteractionRegistration registration : registrations()) {
            byKey.computeIfAbsent(registration.key(), ignored -> new ArrayList<>())
                    .add(registration.type());
        }
        byKey.entrySet().removeIf(entry -> entry.getValue().size() < 2);
        return byKey;
    }

    private static List<InteractionRegistration> registrations() throws Exception {
        String source = Files.readString(ITEM_MANAGER, StandardCharsets.UTF_8);
        Matcher matcher = INTERACTION.matcher(source);
        List<InteractionRegistration> registrations = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key.startsWith("wf_")) {
                registrations.add(new InteractionRegistration(key, matcher.group(2)));
            }
        }
        assertTrue(!registrations.isEmpty(), "No wired interactions found in " + ITEM_MANAGER);
        return registrations;
    }

    private record InteractionRegistration(String key, String type) {}

    private static final class TestItemManager extends ItemManager {
        private void loadDefaults() {
            loadItemInteractions();
        }
    }
}
