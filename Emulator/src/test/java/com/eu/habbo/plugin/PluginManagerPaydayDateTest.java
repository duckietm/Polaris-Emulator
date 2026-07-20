package com.eu.habbo.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManagerPaydayDateTest {

    @TempDir
    Path temporaryDirectory;

    private int configFileCounter;

    @Test
    void paydayTimestampInvertsTheSystemZoneWriterRegardlessOfHotelTimezone() throws Exception {
        long epochSeconds = 1_706_749_323L;
        SimpleDateFormat writer = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String persisted = writer.format(new Date(epochSeconds * 1000L));

        ZoneId system = ZoneId.systemDefault();
        boolean systemIsUtc =
                system.getRules().getOffset(Instant.ofEpochSecond(epochSeconds)).getTotalSeconds() == 0;
        String hotelTimezone = systemIsUtc ? "America/Los_Angeles" : "UTC";

        assertEquals(epochSeconds, parse(hotelTimezone, persisted));
    }

    @Test
    void paydayTimestampRetainsTheLegacyLenientParser() throws Exception {
        assertEquals(parse("UTC", "2024-02-01 00:00:00"), parse("UTC", "2024-01-32 00:00:00"));
    }

    @Test
    void paydayTimestampSaturatesAndRejectsInvalidValues() throws Exception {
        assertEquals(Integer.MAX_VALUE, parse("UTC", "2040-01-01 00:00:00"));
        assertEquals(Integer.MAX_VALUE, parse("UTC", "definitely not a date"));
    }

    private long parse(String hotelTimezone, String value) throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        Field runtimeField = Emulator.class.getDeclaredField("polarisRuntime");
        configField.setAccessible(true);
        runtimeField.setAccessible(true);
        Object previousConfig = configField.get(null);
        Object previousRuntime = runtimeField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config-" + (this.configFileCounter++) + ".ini");
        Files.writeString(configFile, "hotel.timezone=" + hotelTimezone + "\n");
        runtimeField.set(null, null);
        configField.set(null, new ConfigurationManager(configFile.toString()));
        try {
            return PluginManager.parsePaydayTimestamp(value);
        } finally {
            configField.set(null, previousConfig);
            runtimeField.set(null, previousRuntime);
        }
    }
}
