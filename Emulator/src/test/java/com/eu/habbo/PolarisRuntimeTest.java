package com.eu.habbo;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.CryptoConfig;
import com.eu.habbo.core.DatabaseLogger;
import com.eu.habbo.core.Logging;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.util.imager.badges.BadgeImager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class PolarisRuntimeTest {

    @Test
    void emulatorFacadeDelegatesToEveryInstalledRuntimeService()
            throws Exception {
        PolarisRuntime runtime = new PolarisRuntime(() -> {
        });
        Map<Class<?>, RuntimeService<?>> services = new LinkedHashMap<>();
        services.put(
                ConfigurationManager.class,
                service(runtime::installConfiguration, Emulator::getConfig));
        services.put(
                CryptoConfig.class,
                service(runtime::installCrypto, Emulator::getCrypto));
        services.put(
                TextsManager.class,
                service(runtime::installTexts, Emulator::getTexts));
        services.put(
                Database.class,
                service(runtime::installDatabase, Emulator::getDatabase));
        services.put(
                DatabaseLogger.class,
                service(
                        runtime::installDatabaseLogger,
                        Emulator::getDatabaseLogger));
        services.put(
                GameServer.class,
                service(runtime::installGameServer, Emulator::getGameServer));
        services.put(
                RCONServer.class,
                service(runtime::installRconServer, Emulator::getRconServer));
        services.put(
                Logging.class,
                service(runtime::installLogging, Emulator::getLogging));
        services.put(
                ThreadPooling.class,
                service(runtime::installThreading, Emulator::getThreading));
        services.put(
                GameEnvironment.class,
                service(
                        runtime::installGameEnvironment,
                        Emulator::getGameEnvironment));
        services.put(
                PluginManager.class,
                service(
                        runtime::installPluginManager,
                        Emulator::getPluginManager));
        services.put(
                BadgeImager.class,
                service(
                        runtime::installBadgeImager,
                        Emulator::getBadgeImager));

        Field runtimeOwner = Emulator.class.getDeclaredField("polarisRuntime");
        runtimeOwner.setAccessible(true);
        Object originalRuntimeOwner = runtimeOwner.get(null);
        try {
            runtimeOwner.set(null, runtime);
            for (Map.Entry<Class<?>, RuntimeService<?>> entry
                    : services.entrySet()) {
                assertRuntimeService(entry.getKey(), entry.getValue());
            }
        } finally {
            runtimeOwner.set(null, originalRuntimeOwner);
        }
    }

    @Test
    void uninstalledRuntimeServiceFallsBackToLegacyFacadeField()
            throws Exception {
        Field runtimeOwner = Emulator.class.getDeclaredField("polarisRuntime");
        Field configField = Emulator.class.getDeclaredField("config");
        runtimeOwner.setAccessible(true);
        configField.setAccessible(true);
        Object originalRuntimeOwner = runtimeOwner.get(null);
        Object originalConfig = configField.get(null);
        ConfigurationManager legacyConfig = mock(ConfigurationManager.class);
        try {
            runtimeOwner.set(null, new PolarisRuntime(() -> {
            }));
            configField.set(null, legacyConfig);

            assertSame(legacyConfig, Emulator.getConfig());
        } finally {
            configField.set(null, originalConfig);
            runtimeOwner.set(null, originalRuntimeOwner);
        }
    }

    private static <T> RuntimeService<T> service(
            Consumer<T> installer,
            Supplier<T> facadeGetter) {
        return new RuntimeService<>(installer, facadeGetter);
    }

    private static <T> void assertRuntimeService(
            Class<T> type,
            RuntimeService<?> untypedService) {
        @SuppressWarnings("unchecked")
        RuntimeService<T> service = (RuntimeService<T>) untypedService;
        T installed = mock(type);
        service.installer().accept(installed);

        assertSame(installed, service.facadeGetter().get(), type.getName());
    }

    private record RuntimeService<T>(
            Consumer<T> installer,
            Supplier<T> facadeGetter) {
    }
}
