package com.eu.habbo.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationBindingBehaviorTest {

    private ConfigurationManager originalConfig;
    private boolean originalShowPublicRooms;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void installConfiguration() throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        this.originalConfig = (ConfigurationManager) field.get(null);
        this.originalShowPublicRooms = RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB;

        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(
                config,
                "discount.additional.thresholds=invalid\n"
                        + "hotel.navigator.populartab.publics=true\n");
        field.set(null, new ConfigurationManager(config.toString()));
    }

    @AfterEach
    void restoreConfiguration() throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, this.originalConfig);
        RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB = this.originalShowPublicRooms;
    }

    @Test
    void malformedValueDoesNotPreventLaterKeysFromApplying() {
        assertDoesNotThrow(
                () -> PluginManager.globalOnConfigurationUpdated(
                        new EmulatorConfigUpdatedEvent()));
        assertTrue(RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB);
    }
}
