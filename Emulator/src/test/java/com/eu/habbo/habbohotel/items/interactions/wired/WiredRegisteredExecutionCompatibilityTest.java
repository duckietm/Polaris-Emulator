package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.WiredInteractionRegistryFixture;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        assertEquals(
                253,
                WiredInteractionRegistryFixture.wiredTypes().size(),
                "Review every added or removed registered wired execution type");
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

        for (Class<? extends InteractionWired> type : WiredInteractionRegistryFixture.wiredTypes().stream()
                .sorted(Comparator.comparing(Class::getName))
                .toList()) {
            lines.add(outcome(type));
        }
        return lines;
    }

    private static String outcome(Class<? extends InteractionWired> type) {
        try {
            InteractionWired item = WiredInteractionRegistryFixture.instantiate(type);
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
        return "ERROR:"
                + WiredInteractionRegistryFixture.rootCause(failure).getClass().getName();
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
