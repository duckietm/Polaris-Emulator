package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

record RoomDependencies(ConnectionProvider database, PersistenceScheduler persistence) {

    RoomDependencies {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(persistence, "persistence");
    }

    RoomDependencies(ConnectionProvider database) {
        this(database, Runnable::run);
    }

    static RoomDependencies runtime() {
        return new RoomDependencies(() -> Emulator.getDatabase().getDataSource().getConnection());
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
