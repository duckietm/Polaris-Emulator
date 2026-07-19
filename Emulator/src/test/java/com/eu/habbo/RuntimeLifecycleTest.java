package com.eu.habbo;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.EmulatorStartShutdownEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStoppedEvent;
import com.eu.habbo.threading.ThreadPooling;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeLifecycleTest {

    @Test
    void shutdownUsesReversePhasesAndIsolatesFailures() {
        List<String> calls = new ArrayList<>();
        PolarisRuntime runtime =
                new PolarisRuntime(() -> calls.add("sessions.dispose"));
        ConfigurationManager configuration =
                mock(ConfigurationManager.class);
        configuration.loaded = true;
        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        GameServer gameServer = mock(GameServer.class);
        RCONServer rconServer = mock(RCONServer.class);
        ThreadPooling threading = mock(ThreadPooling.class);
        PersistenceExecutor persistence =
                mock(PersistenceExecutor.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        PluginManager plugins = mock(PluginManager.class);

        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.isClosed()).thenReturn(false);
        doAnswer(invocation -> {
            calls.add("threading.reject-new-work");
            return null;
        }).when(threading).setCanAdd(false);
        doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (event instanceof EmulatorStartShutdownEvent) {
                calls.add("plugins.before-shutdown");
            } else if (event instanceof EmulatorStoppedEvent) {
                calls.add("plugins.after-hotel");
            }
            return event;
        }).when(plugins).fireEvent(any());
        doAnswer(invocation -> {
            calls.add("rcon.stop");
            throw new IllegalStateException("expected");
        }).when(rconServer).stop();
        doAnswer(invocation -> {
            calls.add("game.stop");
            return null;
        }).when(gameServer).stop();
        doAnswer(invocation -> {
            calls.add("hotel.dispose");
            return null;
        }).when(environment).dispose();
        doAnswer(invocation -> {
            calls.add("plugins.dispose");
            return null;
        }).when(plugins).dispose();
        doAnswer(invocation -> {
            calls.add("threading.shutdown");
            return null;
        }).when(threading).shutDown();
        doAnswer(invocation -> {
            calls.add("persistence.shutdown");
            return null;
        }).when(persistence).shutDown();
        doAnswer(invocation -> {
            calls.add("configuration.save");
            return null;
        }).when(configuration).saveToDatabase();
        doAnswer(invocation -> {
            calls.add("database.dispose");
            return null;
        }).when(database).dispose();

        runtime.installConfiguration(configuration);
        runtime.installDatabase(database);
        runtime.installThreading(threading);
        runtime.installPersistenceExecutor(persistence);
        runtime.installPluginManager(plugins);
        runtime.installGameEnvironment(environment);
        runtime.installGameServer(gameServer);
        runtime.installRconServer(rconServer);

        runtime.shutdown();
        runtime.shutdown();

        assertEquals(List.of(
                "threading.reject-new-work",
                "plugins.before-shutdown",
                "rcon.stop",
                "game.stop",
                "sessions.dispose",
                "hotel.dispose",
                "plugins.after-hotel",
                "plugins.dispose",
                "threading.shutdown",
                "persistence.shutdown",
                "configuration.save",
                "database.dispose"), calls);
    }

    @Test
    void partialRuntimeDisposesOnlyInstalledResources() {
        List<String> calls = new ArrayList<>();
        PolarisRuntime runtime = new PolarisRuntime(() -> {
        });
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(null);
        doAnswer(invocation -> {
            calls.add("database.dispose");
            return null;
        }).when(database).dispose();
        runtime.installDatabase(database);

        runtime.shutdown();

        assertEquals(List.of("database.dispose"), calls);
    }

    @Test
    void shutdownDoesNotPersistConfigurationBeforeDatabaseLoadingCompletes() {
        PolarisRuntime runtime = new PolarisRuntime(() -> {
        });
        ConfigurationManager configuration =
                mock(ConfigurationManager.class);
        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.isClosed()).thenReturn(false);
        runtime.installConfiguration(configuration);
        runtime.installDatabase(database);

        runtime.shutdown();

        verify(configuration, never()).saveToDatabase();
        verify(database).dispose();
    }
}
