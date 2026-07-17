package com.eu.habbo.database.factories;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Test factory for {@code users} rows — the Java equivalent of a Laravel model
 * factory. Inserts a valid user (respecting the table's NOT NULL constraints)
 * and returns its id. Defaults are deterministic (usernames use a sequence, not
 * randomness) so a failing test is reproducible.
 *
 * <p>Insert column set verified against the real schema. Other entities
 * (RoomFactory, BotFactory, …) follow the same shape.
 *
 * <pre>{@code
 *   int id = UserFactory.create(ds, u -> u.username("alice").credits(500));
 * }</pre>
 */
public final class UserFactory {

    private static final AtomicInteger SEQ = new AtomicInteger();

    public static final class Spec {
        String username = "user_" + SEQ.incrementAndGet();
        String password = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        String mail = null;                                 // defaults to username@test.local
        int credits = 0;
        String look = "hd-180-1.hr-100-0";
        String motto = "";
        String gender = "M";
        int rank = 1;
        String ip = "127.0.0.1";

        public Spec username(String v) { this.username = v; return this; }
        public Spec password(String v) { this.password = v; return this; }
        public Spec mail(String v) { this.mail = v; return this; }
        public Spec credits(int v) { this.credits = v; return this; }
        public Spec look(String v) { this.look = v; return this; }
        public Spec motto(String v) { this.motto = v; return this; }
        public Spec gender(String v) { this.gender = v; return this; }
        public Spec rank(int v) { this.rank = v; return this; }
        public Spec ip(String v) { this.ip = v; return this; }
    }

    private UserFactory() {
    }

    public static int create(DataSource dataSource) {
        return create(dataSource, s -> {});
    }

    public static void resetSequence() {
        SEQ.set(0);
    }

    public static int create(DataSource dataSource, Consumer<Spec> customiser) {
        Spec spec = new Spec();
        customiser.accept(spec);
        if (spec.mail == null) {
            spec.mail = spec.username + "@test.local";
        }

        String sql = "INSERT INTO users "
                + "(username, password, mail, auth_ticket, account_created, last_online, look, motto, gender, rank, credits, home_room, ip_register, ip_current, machine_id) "
                + "VALUES (?, ?, ?, '', UNIX_TIMESTAMP(), UNIX_TIMESTAMP(), ?, ?, ?, ?, ?, 0, ?, ?, '')";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, spec.username);
            statement.setString(2, spec.password);
            statement.setString(3, spec.mail);
            statement.setString(4, spec.look);
            statement.setString(5, spec.motto);
            statement.setString(6, spec.gender);
            statement.setInt(7, spec.rank);
            statement.setInt(8, spec.credits);
            statement.setString(9, spec.ip);
            statement.setString(10, spec.ip);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new IllegalStateException("UserFactory insert returned no generated id");
        } catch (Exception e) {
            throw new IllegalStateException("UserFactory could not create a user", e);
        }
    }
}
