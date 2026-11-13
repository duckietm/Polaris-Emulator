package polaris.hotel.users.firstlogin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Who gets the first-login setup, and that answering it gives at most one room. */
class FirstLoginSetupServiceTest {
    private static final int USER = 7;

    private final DataSource dataSource = mock(DataSource.class);
    /** users_first_login_setup: user id to completed. */
    private final Map<Integer, Integer> rows = new HashMap<>();

    private final Set<Integer> roomOwners = new HashSet<>();
    private final List<Integer> cloned = new ArrayList<>();
    private FirstLoginSetupService service;

    @BeforeEach
    void setUp() throws Exception {
        when(this.dataSource.getConnection()).thenAnswer(call -> {
            Connection connection = mock(Connection.class);
            when(connection.prepareStatement(anyString())).thenAnswer(prepare -> statement(prepare.getArgument(0)));
            return connection;
        });
        this.service = new FirstLoginSetupService(this.dataSource, (connection, templateId, userId, userName) -> {
            this.cloned.add(templateId);
            this.roomOwners.add(userId);
            return 900 + templateId;
        });
    }

    @Test
    void anAccountWithoutARoomIsAskedAtItsFirstLogin() {
        this.service.begin(USER, 0);

        assertTrue(this.service.isPending(USER));
    }

    @Test
    void anAccountThatAlreadyHasARoomIsNotAsked() {
        this.roomOwners.add(USER);
        this.service.begin(USER, 0);
        this.service.begin(8, 55);

        assertFalse(this.service.isPending(USER));
        assertFalse(this.service.isPending(8));
    }

    @Test
    void anAccountOfTheRegisterScreenIsNotAsked() {
        this.rows.put(USER, 1);

        this.service.begin(USER, 0);

        assertFalse(this.service.isPending(USER));
    }

    @Test
    void anAccountFromBeforeIsNotAsked() {
        assertFalse(this.service.isPending(USER));
        assertEquals(0, this.service.complete(USER, "ann", 3));
        assertTrue(this.cloned.isEmpty());
    }

    @Test
    void theOfferStaysUntilItIsAnswered() {
        this.service.begin(USER, 0);
        this.service.begin(USER, 0);

        assertTrue(this.service.isPending(USER));
    }

    @Test
    void choosingARoomGivesItOnce() {
        this.service.begin(USER, 0);

        assertEquals(903, this.service.complete(USER, "ann", 3));
        assertEquals(0, this.service.complete(USER, "ann", 3));

        assertEquals(List.of(3), this.cloned);
        assertFalse(this.service.isPending(USER));
    }

    @Test
    void choosingNoRoomEndsTheOffer() {
        this.service.begin(USER, 0);

        assertEquals(0, this.service.complete(USER, "ann", 0));

        assertTrue(this.cloned.isEmpty());
        assertFalse(this.service.isPending(USER));
    }

    @Test
    void aRoomMadeInTheMeantimeIsNotDoubled() {
        this.service.begin(USER, 0);
        this.roomOwners.add(USER);

        assertEquals(0, this.service.complete(USER, "ann", 3));

        assertTrue(this.cloned.isEmpty());
        assertFalse(this.service.isPending(USER));
    }

    private PreparedStatement statement(String sql) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        Map<Integer, Integer> ints = new HashMap<>();
        doAnswer(call -> ints.put(call.getArgument(0), call.getArgument(1)))
                .when(statement)
                .setInt(anyInt(), anyInt());

        when(statement.executeUpdate()).thenAnswer(call -> {
            if (sql.startsWith("INSERT IGNORE")) {
                return this.rows.putIfAbsent(ints.get(1), ints.get(2)) == null ? 1 : 0;
            }
            if (sql.startsWith("UPDATE users_first_login_setup")) {
                return this.rows.replace(ints.get(1), 0, 1) ? 1 : 0;
            }
            throw new IllegalStateException(sql);
        });
        when(statement.executeQuery()).thenAnswer(call -> {
            boolean found;
            if (sql.contains("FROM users_first_login_setup")) {
                found = Integer.valueOf(0).equals(this.rows.get(ints.get(1)));
            } else if (sql.contains("FROM rooms")) {
                found = this.roomOwners.contains(ints.get(1));
            } else {
                throw new IllegalStateException(sql);
            }
            ResultSet set = mock(ResultSet.class);
            when(set.next()).thenReturn(found, false);
            return set;
        });
        return statement;
    }
}
