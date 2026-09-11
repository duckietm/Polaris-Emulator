package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.IntSupplier;

record RoomDependencies(ConnectionProvider database, PersistenceScheduler persistence, IntSupplier unixTime) {

    RoomDependencies {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(unixTime, "unixTime");
    }

    RoomDependencies(ConnectionProvider database) {
        this(database, Runnable::run, RoomDependencies::currentUnixTime);
    }

    RoomDependencies(ConnectionProvider database, PersistenceScheduler persistence) {
        this(database, persistence, RoomDependencies::currentUnixTime);
    }

    static RoomDependencies runtime() {
        return new RoomDependencies(() -> Emulator.getDatabase().getDataSource().getConnection());
    }

    private static int currentUnixTime() {
        return Math.toIntExact(Instant.now().getEpochSecond());
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection openConnection() throws SQLException;
    }

    @FunctionalInterface
    interface PersistenceScheduler {
        void execute(Runnable task);
    }
}
