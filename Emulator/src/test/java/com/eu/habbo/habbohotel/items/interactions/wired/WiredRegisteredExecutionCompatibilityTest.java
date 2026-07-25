package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/**
 * Runs one deterministic, actor-free execution probe against every registered wired implementation.
 * The matrix renders exception classes when a probe fails so a regression is diagnosable, while the
 * companion invariant requires the reviewed baseline to contain no exception outcomes. It remains a
 * compatibility tripwire for refactoring, not proof of meaningful configured behavior or final room
 * state; family-specific tests own those assertions.
 *
 * <p>Regeneration is an explicit review action:
 *
 * <pre>
 * mvn test -Dtest=WiredRegisteredExecutionCompatibilityTest \
 *   -Dpolaris.wired.execution.regenerate=true
 * </pre>
 */
class WiredRegisteredExecutionCompatibilityTest {

    private static final String REGENERATE_PROPERTY = "polaris.wired.execution.regenerate";
    private static final Path CONTRACT =
            Path.of("src", "test", "resources", "wired-compatibility", "registered-execution-v1.txt");
    private static final Path ITEM_MANAGER =
            Path.of("src", "main", "java", "com", "eu", "habbo", "habbohotel", "items", "ItemManager.java");
    private static final Pattern IMPORT = Pattern.compile("import\\s+([A-Za-z0-9_$.]+);", Pattern.MULTILINE);
    private static final Pattern INTERACTION = Pattern.compile(
            "new\\s+ItemInteraction\\(\\s*\"(wf_[^\"]+)\"\\s*,\\s*([A-Za-z0-9_$.]+)\\.class\\s*\\)", Pattern.MULTILINE);

    @Test
    void everyRegisteredWiredExecutionProbeStaysStable() throws Exception {
        List<String> actual = snapshot();
        if (System.getProperty(REGENERATE_PROPERTY) != null) {
            Files.createDirectories(CONTRACT.getParent());
            Files.write(CONTRACT, actual, StandardCharsets.UTF_8);
            return;
        }

        assertTrue(
                Files.isRegularFile(CONTRACT),
                "Missing registered execution contract; regenerate with -D" + REGENERATE_PROPERTY + "=true");
        assertEquals(
                Files.readAllLines(CONTRACT, StandardCharsets.UTF_8),
                actual,
                "Registered wired execution behavior changed. Keep the current result or review the fixture as "
                        + "an explicit correctness or compatibility change.");
    }

    @Test
    void executionMatrixCoversEveryRegisteredWiredClass() throws Exception {
        assertEquals(235, wiredTypes().size(), "Review every added or removed registered wired execution type");
    }

    @Test
    void unconfiguredRegisteredWiredProbesFailClosedWithoutThrowing() throws Exception {
        List<String> failures =
                snapshot().stream().filter(line -> line.contains("ERROR:")).toList();

        assertEquals(
                List.of(),
                failures,
                "A newly placed or not-yet-configured WIRED box must be a safe no-op instead of throwing");
    }

    private static List<String> snapshot() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# Polaris registered wired actor-free execution matrix v1");
        lines.add("# One deterministic unconfigured probe per registered InteractionWired class.");
        lines.add("# ERROR records current failure behavior; it is not approval of that behavior.");
        lines.add("");

        for (Class<? extends InteractionWired> type : wiredTypes().stream()
                .sorted(Comparator.comparing(Class::getName))
                .toList()) {
            lines.add(outcome(type));
        }
        return lines;
    }

    private static String outcome(Class<? extends InteractionWired> type) {
        try {
            InteractionWired item = instantiate(type);
            Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);

            if (item instanceof InteractionWiredTrigger trigger) {
                WiredEvent.Type eventType = trigger.listensTo();
                WiredEvent event =
                        WiredEvent.builder(eventType, room).sourceItem(item).build();
                return row(
                        type,
                        "TRIGGER",
                        "type=" + trigger.getType().name(),
                        "event=" + eventType.name(),
                        "actor=" + trigger.requiresActor(),
                        "match=" + call(() -> trigger.matches(item, event)));
            }

            WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build();
            WiredContext context = new WiredContext(
                    event, null, mock(WiredServices.class, Answers.RETURNS_DEEP_STUBS), new WiredState(100));

            if (item instanceof InteractionWiredCondition condition) {
                return row(
                        type,
                        "CONDITION",
                        "type=" + condition.getType().name(),
                        "operator=" + condition.operator().name(),
                        "evaluate=" + call(() -> condition.evaluate(context)));
            }

            if (item instanceof InteractionWiredEffect effect) {
                return row(
                        type,
                        "EFFECT",
                        "type=" + effect.getType().name(),
                        "actor=" + effect.requiresActor(),
                        "execute=" + run(() -> effect.execute(context)));
            }

            if (item instanceof InteractionWiredExtra extra) {
                return row(
                        type,
                        "EXTRA",
                        "configured=" + extra.hasConfiguration(),
                        "execute=" + call(() -> extra.execute(null, room, new Object[0])));
            }

            return row(type, "WIRED", "execute=" + call(() -> item.execute(null, room, new Object[0])));
        } catch (Throwable failure) {
            return row(type, "CONSTRUCT", error(failure));
        }
    }

    private static String row(Class<?> type, String family, String... fields) {
        return type.getName() + " | " + family + " | " + String.join(" | ", fields);
    }

    private static String call(CheckedBooleanCall call) {
        try {
            return Boolean.toString(call.run());
        } catch (Throwable failure) {
            return error(failure);
        }
    }

    private static String run(CheckedRunnable runnable) {
        try {
            runnable.run();
            return "OK";
        } catch (Throwable failure) {
            return error(failure);
        }
    }

    private static String error(Throwable failure) {
        return "ERROR:" + rootCause(failure).getClass().getName();
    }

    private static InteractionWired instantiate(Class<? extends InteractionWired> type) throws Exception {
        Constructor<? extends InteractionWired> constructor =
                type.getConstructor(int.class, int.class, Item.class, String.class, int.class, int.class);
        Item baseItem = mock(Item.class);
        when(baseItem.getSpriteId()).thenReturn(123);
        when(baseItem.getName()).thenReturn("wired_fixture");
        return constructor.newInstance(4242, 7, baseItem, "0", 0, 0);
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends InteractionWired>> wiredTypes() throws Exception {
        String source = Files.readString(ITEM_MANAGER, StandardCharsets.UTF_8);
        Map<String, String> imports = imports(source);
        Matcher matcher = INTERACTION.matcher(source);
        Set<Class<? extends InteractionWired>> result = new LinkedHashSet<>();
        while (matcher.find()) {
            Class<?> type = resolveType(imports, matcher.group(2));
            if (InteractionWired.class.isAssignableFrom(type)) {
                result.add((Class<? extends InteractionWired>) type);
            }
        }
        return result;
    }

    private static Map<String, String> imports(String source) {
        Map<String, String> imports = new LinkedHashMap<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            imports.put(name.substring(name.lastIndexOf('.') + 1), name);
        }
        return imports;
    }

    private static Class<?> resolveType(Map<String, String> imports, String sourceName) throws ClassNotFoundException {
        int nestedSeparator = sourceName.indexOf('.');
        String outerName = nestedSeparator < 0 ? sourceName : sourceName.substring(0, nestedSeparator);
        String fullyQualifiedOuter = imports.get(outerName);
        if (fullyQualifiedOuter == null) {
            throw new ClassNotFoundException("No import found for registered type " + sourceName);
        }
        String nestedName = nestedSeparator < 0 ? "" : "$" + sourceName.substring(nestedSeparator + 1);
        return Class.forName(fullyQualifiedOuter + nestedName);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable result = failure;
        if (result instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            result = invocation.getCause();
        }
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    @FunctionalInterface
    private interface CheckedBooleanCall {
        boolean run() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
