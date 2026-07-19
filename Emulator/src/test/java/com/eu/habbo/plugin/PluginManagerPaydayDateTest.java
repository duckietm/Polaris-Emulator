package com.eu.habbo.plugin;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginManagerPaydayDateTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void paydayTimestampUsesStrictHotelTimeParsingAndIntSaturation()
            throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(configFile, "hotel.timezone=UTC\n");
        configField.set(null, new ConfigurationManager(configFile.toString()));

        try {
            Method parser =
                    PluginManager.class.getDeclaredMethod(
                            "parsePaydayTimestamp", String.class);
            parser.setAccessible(true);

            assertEquals(
                    1_706_746_923,
                    parser.invoke(null, "2024-02-01 01:02:03"));
            assertEquals(
                    Integer.MAX_VALUE,
                    parser.invoke(null, "2024-02-30 01:02:03"));
            assertEquals(
                    Integer.MAX_VALUE,
                    parser.invoke(null, "2040-01-01 00:00:00"));
        } finally {
            configField.set(null, previousConfig);
        }
    }
}
