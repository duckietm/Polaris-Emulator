package com.eu.habbo.util.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.sql.SQLException;

public class SqlExceptionFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        var proxy = (ThrowableProxy) event.getThrowableProxy();

        if (proxy.getThrowable() instanceof SQLException){
            return FilterReply.ACCEPT;
        }

        return FilterReply.DENY;
    }

}
