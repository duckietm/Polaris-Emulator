package com.eu.habbo.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.users.Habbo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginEventCorrectedBehaviorTest {

    @Test
    void optInDispatchesHandlersByAnnotationPriority() {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager(() -> true);
        PriorityPlugin plugin = new PriorityPlugin(calls);
        manager.getPlugins().add(plugin);
        manager.registerEvents(plugin, plugin);

        manager.fireEvent(new TestEvent());

        assertEquals(List.of("lowest", "low", "normal", "high", "highest", "monitor"), calls);
    }

    @Test
    void optInSkipsIgnoreCancelledHandlersButContinuesOtherHandlers() {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager(() -> true);
        CancellationPlugin plugin = new CancellationPlugin(calls);
        manager.getPlugins().add(plugin);
        manager.registerEvents(plugin, plugin);

        TestEvent event = manager.fireEvent(new TestEvent());

        assertTrue(event.isCancelled());
        assertEquals(List.of("cancel", "receive-cancelled"), calls);
    }

    @Test
    void optInOrdersDefaultAndPluginHandlersTogether() throws Exception {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager(() -> true);
        DefaultHandlers.calls = calls;
        defaultMethods(manager).add(DefaultHandlers.class.getMethod("highDefault", TestEvent.class));
        PluginBeforeDefault plugin = new PluginBeforeDefault(calls);
        manager.getPlugins().add(plugin);
        manager.registerEvents(plugin, plugin);

        manager.fireEvent(new TestEvent());

        assertEquals(List.of("plugin-low", "default-high"), calls);
    }

    @Test
    void handlerTableMutationDoesNotBreakInFlightDispatch() throws Exception {
        PluginManager manager = new PluginManager(() -> false);
        Set<Method> methods = defaultMethods(manager);
        DefaultHandlers.methods = methods;
        methods.add(DefaultHandlers.class.getMethod("clearOne", TestEvent.class));
        methods.add(DefaultHandlers.class.getMethod("clearTwo", TestEvent.class));

        assertDoesNotThrow(() -> manager.fireEvent(new TestEvent()));
    }

    @Test
    void correctedDispatchIsDocumentedAsAnOptIn() throws Exception {
        String example = Files.readString(Path.of("..", "config example", "config.ini.example"));
        String startup = Files.readString(Path.of("src", "main", "java", "com", "eu", "habbo", "Emulator.java"));

        assertTrue(example.contains("polaris.events.honor_priority=false"));
        assertTrue(startup.contains("\"polaris.events.honor_priority\""));
        assertTrue(startup.contains("priority-ordered, cancellation-aware"));
    }

    @SuppressWarnings("unchecked")
    private static Set<Method> defaultMethods(PluginManager manager) throws Exception {
        Field field = PluginManager.class.getDeclaredField("methods");
        field.setAccessible(true);
        return (Set<Method>) field.get(manager);
    }

    public static final class DefaultHandlers {
        private static List<String> calls;
        private static Set<Method> methods;

        @EventHandler(priority = EventPriority.HIGH)
        public static void highDefault(TestEvent event) {
            calls.add("default-high");
        }

        @EventHandler
        public static void clearOne(TestEvent event) {
            methods.clear();
        }

        @EventHandler
        public static void clearTwo(TestEvent event) {
            methods.clear();
        }
    }

    private abstract static class TestPlugin extends HabboPlugin implements EventListener {
        final List<String> calls;

        TestPlugin(List<String> calls) {
            this.calls = calls;
            this.configuration = new HabboPluginConfiguration();
            this.configuration.name = getClass().getSimpleName();
        }

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}

        @Override
        public boolean hasPermission(Habbo habbo, String key) {
            return true;
        }
    }

    private static final class PriorityPlugin extends TestPlugin {
        PriorityPlugin(List<String> calls) {
            super(calls);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void monitor(TestEvent event) {
            calls.add("monitor");
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void lowest(TestEvent event) {
            calls.add("lowest");
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void highest(TestEvent event) {
            calls.add("highest");
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void normal(TestEvent event) {
            calls.add("normal");
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void high(TestEvent event) {
            calls.add("high");
        }

        @EventHandler(priority = EventPriority.LOW)
        public void low(TestEvent event) {
            calls.add("low");
        }
    }

    private static final class CancellationPlugin extends TestPlugin {
        CancellationPlugin(List<String> calls) {
            super(calls);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void cancel(TestEvent event) {
            calls.add("cancel");
            event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void skipCancelled(TestEvent event) {
            calls.add("skipped");
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void receiveCancelled(TestEvent event) {
            calls.add("receive-cancelled");
        }
    }

    private static final class PluginBeforeDefault extends TestPlugin {
        PluginBeforeDefault(List<String> calls) {
            super(calls);
        }

        @EventHandler(priority = EventPriority.LOW)
        public void lowPlugin(TestEvent event) {
            calls.add("plugin-low");
        }
    }

    private static final class TestEvent extends Event {}
}
