package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.IntSupplier;
import polaris.Emulator;

public record RoomDependencies(ConnectionProvider database, PersistenceScheduler persistence, IntSupplier unixTime) {

    public RoomDependencies {
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
    public interface ConnectionProvider {
        Connection openConnection() throws SQLException;
    }

    @FunctionalInterface
    public interface PersistenceScheduler {
        void execute(Runnable task);
    }
}
