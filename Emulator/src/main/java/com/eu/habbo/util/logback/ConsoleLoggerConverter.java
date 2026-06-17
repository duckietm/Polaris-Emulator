package com.eu.habbo.util.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class ConsoleLoggerConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return ConsoleStyle.logger(event == null ? "" : event.getLoggerName(), ConsoleStyle.isRuntimeEnabled());
    }
}
