package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.EmulatorStartShutdownEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStoppedEvent;
import com.eu.habbo.threading.ThreadPooling;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class EmulatorLifecycleCompatibilityTest {

    @Test
    void stoppedIsPublishedOnlyAfterRuntimeResourcesClose() throws Exception {
        AtomicBoolean databaseClosed = new AtomicBoolean();
        PolarisRuntime runtime = new PolarisRuntime(() -> {});
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(null);
        doAnswer(invocation -> {
                    assertFalse(Emulator.stopped);
                    databaseClosed.set(true);
                    return null;
                })
                .when(database)
                .dispose();
        runtime.installDatabase(database);

        Map<Field, Object> originalFields = new LinkedHashMap<>();
        Map<Field, Boolean> originalFlags = new LinkedHashMap<>();
        try {
            install(originalFields, "polarisRuntime", runtime);
            installFlag(originalFlags, "stopped", false);

            Method dispose = Emulator.class.getDeclaredMethod("dispose");
            dispose.setAccessible(true);
            dispose.invoke(null);

            assertTrue(databaseClosed.get());
            assertTrue(Emulator.stopped);
        } finally {
            restore(originalFields);
            restoreFlags(originalFlags);
        }
    }

    @Test
    void shutdownKeepsItsObservableOrderAndContinuesAfterFailure() throws Exception {
        List<String> calls = new ArrayList<>();
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        GameServer gameServer = mock(GameServer.class);
        RCONServer rconServer = mock(RCONServer.class);
        ThreadPooling threading = mock(ThreadPooling.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        PluginManager plugins = mock(PluginManager.class);

        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.isClosed()).thenReturn(false);
        doAnswer(invocation -> {
                    calls.add("threading.reject-new-work");
                    return null;
                })
                .when(threading)
                .setCanAdd(false);
        doAnswer(invocation -> {
                    Object event = invocation.getArgument(0);
                    if (event instanceof EmulatorStartShutdownEvent) {
                        calls.add("plugins.before-shutdown");
                    } else if (event instanceof EmulatorStoppedEvent) {
                        calls.add("plugins.after-hotel");
                    }
                    return event;
                })
                .when(plugins)
                .fireEvent(any());
        doAnswer(invocation -> {
                    calls.add("rcon.stop");
                    throw new IllegalStateException("expected bind teardown failure");
                })
                .when(rconServer)
                .stop();
        doAnswer(invocation -> {
                    calls.add("hotel.dispose");
                    return null;
                })
                .when(environment)
                .dispose();
        doAnswer(invocation -> {
                    calls.add("plugins.dispose");
                    return null;
                })
                .when(plugins)
                .dispose();
        doAnswer(invocation -> {
                    calls.add("configuration.save");
                    return null;
                })
                .when(configuration)
                .saveToDatabase();
        doAnswer(invocation -> {
                    calls.add("game.stop");
                    return null;
                })
                .when(gameServer)
                .stop();
        doAnswer(invocation -> {
                    calls.add("threading.shutdown");
                    return null;
                })
                .when(threading)
                .shutDown();
        doAnswer(invocation -> {
                    calls.add("database.dispose");
                    return null;
                })
                .when(database)
                .dispose();

        Map<Field, Object> originalFields = new LinkedHashMap<>();
        Map<Field, Boolean> originalFlags = new LinkedHashMap<>();
        try {
            install(originalFields, "polarisRuntime", null);
            install(originalFields, "config", configuration);
            install(originalFields, "database", database);
            install(originalFields, "gameServer", gameServer);
            install(originalFields, "rconServer", rconServer);
            install(originalFields, "threading", threading);
            install(originalFields, "gameEnvironment", environment);
            install(originalFields, "pluginManager", plugins);
            installFlag(originalFlags, "isReady", true);
            installFlag(originalFlags, "isShuttingDown", false);
            installFlag(originalFlags, "stopped", false);

            Method dispose = Emulator.class.getDeclaredMethod("dispose");
            dispose.setAccessible(true);
            dispose.invoke(null);

            assertTrue(indexOf(calls, "threading.reject-new-work") < indexOf(calls, "plugins.before-shutdown"));
            assertTrue(indexOf(calls, "plugins.before-shutdown") < indexOf(calls, "hotel.dispose"));
            assertTrue(indexOf(calls, "hotel.dispose") < indexOf(calls, "plugins.after-hotel"));
            assertTrue(indexOf(calls, "plugins.after-hotel") < indexOf(calls, "plugins.dispose"));
            assertTrue(indexOf(calls, "rcon.stop") < indexOf(calls, "database.dispose"));
            assertTrue(calls.containsAll(
                    List.of("rcon.stop", "configuration.save", "game.stop", "threading.shutdown", "database.dispose")));
        } finally {
            restore(originalFields);
            restoreFlags(originalFlags);
        }
    }

    private static int indexOf(List<String> calls, String name) {
        int index = calls.indexOf(name);
        assertTrue(index >= 0, () -> "Missing lifecycle call: " + name);
        return index;
    }

    private static void install(Map<Field, Object> originals, String name, Object value) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        originals.put(field, field.get(null));
        field.set(null, value);
    }

    private static void installFlag(Map<Field, Boolean> originals, String name, boolean value) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        originals.put(field, field.getBoolean(null));
        field.setBoolean(null, value);
    }

    private static void restore(Map<Field, Object> originals) throws IllegalAccessException {
        for (Map.Entry<Field, Object> entry : originals.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    private static void restoreFlags(Map<Field, Boolean> originals) throws IllegalAccessException {
        for (Map.Entry<Field, Boolean> entry : originals.entrySet()) {
            entry.getKey().setBoolean(null, entry.getValue());
        }
    }
}
