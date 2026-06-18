package com.eu.habbo.util;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class HotelDateTimeUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotelDateTimeUtil.class);
    private static final String CONFIG_KEY = "hotel.timezone";
    private static volatile String lastInvalidTimezoneId = null;

    private HotelDateTimeUtil() {
    }

    public static String getTimezoneId() {
        return getZoneId().getId();
    }

    public static ZoneId getZoneId() {
        String configuredZoneId = Emulator.getConfig() != null
                ? Emulator.getConfig().getValue(CONFIG_KEY, ZoneId.systemDefault().getId())
                : ZoneId.systemDefault().getId();

        try {
            lastInvalidTimezoneId = null;
            return ZoneId.of(configuredZoneId.trim());
        } catch (Exception e) {
            if (!configuredZoneId.equals(lastInvalidTimezoneId)) {
                LOGGER.warn("Invalid {} '{}', falling back to system timezone '{}'.", CONFIG_KEY, configuredZoneId, ZoneId.systemDefault().getId());
                lastInvalidTimezoneId = configuredZoneId;
            }
            return ZoneId.systemDefault();
        }
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(getZoneId());
    }

    public static LocalDateTime localDateTimeNow() {
        return LocalDateTime.now(getZoneId());
    }

    public static LocalDate localDateNow() {
        return LocalDate.now(getZoneId());
    }

    public static LocalTime localTimeNow() {
        return LocalTime.now(getZoneId());
    }

    public static long toEpochSecond(LocalDateTime dateTime) {
        return dateTime.atZone(getZoneId()).toEpochSecond();
    }
}
