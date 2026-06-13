package com.eu.habbo.util.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class ConsoleLevelConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return ConsoleStyle.level(event == null ? null : event.getLevel(), ConsoleStyle.isRuntimeEnabled());
    }
}
