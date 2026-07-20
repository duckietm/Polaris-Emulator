package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoomModelRepositoryCharacterizationTest {

    private ConfigurationManager originalConfig;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void installConfiguration() throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        this.originalConfig = (ConfigurationManager) field.get(null);
        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(config, "");
        field.set(null, new ConfigurationManager(config.toString()));
    }

    @AfterEach
    void restoreConfiguration() throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, this.originalConfig);
    }

    @Test
    void layoutNamesReflectTheLoadedModelCache() throws Exception {
        RoomManager manager = new RoomManager(false);

        assertFalse(manager.layoutExists("model_a"));
        modelNames(manager).add("model_a");

        assertTrue(manager.layoutExists("model_a"));
    }

    @Test
    void cachedModelDataCreatesAnIndependentLayoutPerRoom() throws Exception {
        RoomManager manager = new RoomManager(false);
        layoutCache(manager).put("model_a", layoutData("model_a"));

        RoomLayout first = manager.loadLayout("model_a", mock(Room.class));
        RoomLayout second = manager.loadLayout("model_a", mock(Room.class));

        assertNotSame(first, second);
    }

    @SuppressWarnings("unchecked")
    private static List<String> modelNames(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("mapNames");
        field.setAccessible(true);
        return (List<String>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, RoomManager.RoomLayoutData> layoutCache(RoomManager manager)
            throws Exception {
        Field field = RoomManager.class.getDeclaredField("layoutCache");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, RoomManager.RoomLayoutData>) field.get(manager);
    }

    private static RoomManager.RoomLayoutData layoutData(String name) throws Exception {
        ResultSet row = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getString" -> "name".equals(arguments[0]) ? name : "0\r0";
                    case "getInt" -> 0;
                    default -> null;
                });
        return new RoomManager.RoomLayoutData(row);
    }
}
