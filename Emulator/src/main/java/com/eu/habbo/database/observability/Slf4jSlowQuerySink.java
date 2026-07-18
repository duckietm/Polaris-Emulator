package com.eu.habbo.database.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4jSlowQuerySink implements SlowQuerySink {

    private static final Logger LOGGER = LoggerFactory.getLogger("database.slow_query");

    @Override
    public void accept(SlowQueryEvent event) {
        PoolSnapshot pool = event.pool();
        LOGGER.warn(
                "event=database_slow_query duration_ms={} operation={} success={} sql_state={} vendor_code={} "
                        + "fingerprint={} thread={} pool_active={} pool_idle={} pool_total={} pool_awaiting={} sql=\"{}\"",
                event.durationMs(),
                event.operation(),
                event.success(),
                event.sqlState(),
                event.vendorCode(),
                event.fingerprint(),
                event.threadName(),
                pool.active(),
                pool.idle(),
                pool.total(),
                pool.awaiting(),
                event.sql());
    }
}
