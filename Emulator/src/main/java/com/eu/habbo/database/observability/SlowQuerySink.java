package com.eu.habbo.database.observability;

@FunctionalInterface
public interface SlowQuerySink {
    void accept(SlowQueryEvent event);
}
