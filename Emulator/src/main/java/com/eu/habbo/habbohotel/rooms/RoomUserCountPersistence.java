package com.eu.habbo.habbohotel.rooms;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RoomUserCountPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserCountPersistence.class);

    private final IntSupplier userCount;
    private final CountWriter writer;
    private final Executor executor;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    RoomUserCountPersistence(IntSupplier userCount, CountWriter writer, Executor executor) {
        this.userCount = Objects.requireNonNull(userCount, "userCount");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    void schedule() {
        this.dirty.set(true);
        if (this.scheduled.compareAndSet(false, true)) {
            this.executor.execute(this::flush);
        }
    }

    private void flush() {
        try {
            while (this.dirty.getAndSet(false)) {
                this.writer.write(this.userCount.getAsInt());
            }
        } catch (SQLException exception) {
            LOGGER.error("Unable to persist room user count", exception);
        } finally {
            this.scheduled.set(false);
            if (this.dirty.get() && this.scheduled.compareAndSet(false, true)) {
                this.executor.execute(this::flush);
            }
        }
    }

    @FunctionalInterface
    interface CountWriter {
        void write(int userCount) throws SQLException;
    }
}
