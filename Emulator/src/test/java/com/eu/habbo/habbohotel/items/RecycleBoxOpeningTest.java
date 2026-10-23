package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Opening a recycler box commits the claimed prize, the reward and the deleted box together, or nothing. */
class RecycleBoxOpeningTest {
    private static final int USER_ID = 7;
    private static final int BOX_ID = 601;
    private static final int PRIZE_BASE_ID = 230;
    private static final int REWARD_ID = 900;

    private final List<String> calls = new ArrayList<>();
    private int prize;
    private int prizeRowsDeleted;
    private int boxRowsDeleted;
    private boolean insertFails;
    private boolean rewardKnown;

    private Connection connection;
    private ItemManager items;
    private HabboItem box;
    private HabboItem reward;

    @BeforeEach
    void setUp() throws SQLException {
        this.prize = PRIZE_BASE_ID;
        this.prizeRowsDeleted = 1;
        this.boxRowsDeleted = 1;
        this.insertFails = false;
        this.rewardKnown = true;

        this.connection = mock(Connection.class);
        when(this.connection.getAutoCommit()).thenReturn(true);
        when(this.connection.prepareStatement(anyString())).thenAnswer(i -> this.statement(i.getArgument(0)));
        when(this.connection.prepareStatement(anyString(), anyInt())).thenAnswer(i -> this.statement(i.getArgument(0)));
        doAnswer(i -> this.calls.add("commit")).when(this.connection).commit();
        doAnswer(i -> this.calls.add("rollback")).when(this.connection).rollback();

        this.reward = mock(HabboItem.class);
        this.items = mock(ItemManager.class);
        when(this.items.loadHabboItem(any(ResultSet.class))).thenAnswer(i -> this.rewardKnown ? this.reward : null);

        this.box = mock(HabboItem.class);
        when(this.box.getId()).thenReturn(BOX_ID);
    }

    @Test
    void aBoxWithAPrizePaysItOnceAndGoes() throws SQLException {
        RecycleBoxOpening opening = this.open();

        assertEquals(RecycleBoxOpening.Result.OPENED, opening.result());
        assertSame(this.reward, opening.reward());
        assertEquals(
                List.of(
                        "select prize " + BOX_ID,
                        "delete prize " + BOX_ID,
                        "insert reward " + PRIZE_BASE_ID + " for " + USER_ID,
                        "select reward " + REWARD_ID,
                        "delete box " + BOX_ID + " of " + USER_ID,
                        "commit"),
                this.calls);
    }

    @Test
    void aBoxWithoutAPrizeGoesWithNothing() throws SQLException {
        this.prize = 0;

        RecycleBoxOpening opening = this.open();

        assertEquals(RecycleBoxOpening.Result.EMPTY, opening.result());
        assertNull(opening.reward());
        assertEquals(
                List.of("select prize " + BOX_ID, "delete box " + BOX_ID + " of " + USER_ID, "commit"), this.calls);
    }

    @Test
    void aPrizeAlreadyClaimedLeavesTheBox() throws SQLException {
        this.prizeRowsDeleted = 0;

        assertEquals(RecycleBoxOpening.FAILED, this.open());
        assertEquals(List.of("select prize " + BOX_ID, "delete prize " + BOX_ID, "rollback"), this.calls);
    }

    @Test
    void anUnknownRewardLeavesTheBoxAndThePrize() throws SQLException {
        this.rewardKnown = false;

        assertEquals(RecycleBoxOpening.FAILED, this.open());
        assertEquals("rollback", this.calls.getLast());
        assertEquals(false, this.calls.contains("delete box " + BOX_ID + " of " + USER_ID));
    }

    @Test
    void aBoxNoLongerTheirsLeavesThePrize() throws SQLException {
        this.boxRowsDeleted = 0;

        assertEquals(RecycleBoxOpening.FAILED, this.open());
        assertEquals("rollback", this.calls.getLast());
    }

    @Test
    void aDatabaseErrorRollsEverythingBack() {
        this.insertFails = true;

        assertThrows(SQLException.class, this::open);
        assertEquals("rollback", this.calls.getLast());
    }

    private RecycleBoxOpening open() throws SQLException {
        return RecycleBoxOpening.open(this.connection, this.items, USER_ID, this.box);
    }

    private PreparedStatement statement(String sql) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        int[] params = new int[3];
        doAnswer(i -> params[i.<Integer>getArgument(0)] = i.getArgument(1))
                .when(statement)
                .setInt(anyInt(), anyInt());

        if (sql.startsWith("SELECT base_item_reward")) {
            when(statement.executeQuery()).thenAnswer(i -> {
                this.calls.add("select prize " + params[1]);
                return this.rows(this.prize > 0, "base_item_reward", this.prize);
            });
        } else if (sql.startsWith("DELETE FROM items_presents")) {
            when(statement.executeUpdate()).thenAnswer(i -> {
                this.calls.add("delete prize " + params[1]);
                return this.prizeRowsDeleted;
            });
        } else if (sql.startsWith("INSERT INTO items")) {
            when(statement.execute()).thenAnswer(i -> {
                this.calls.add("insert reward " + params[2] + " for " + params[1]);
                if (this.insertFails) throw new SQLException("insert failed");
                return false;
            });
            ResultSet keys = this.rows(true, null, 0);
            when(keys.getInt(1)).thenReturn(REWARD_ID);
            when(statement.getGeneratedKeys()).thenReturn(keys);
        } else if (sql.startsWith("SELECT * FROM items")) {
            when(statement.executeQuery()).thenAnswer(i -> {
                this.calls.add("select reward " + params[1]);
                return this.rows(true, null, 0);
            });
        } else if (sql.startsWith("DELETE FROM items WHERE")) {
            when(statement.executeUpdate()).thenAnswer(i -> {
                this.calls.add("delete box " + params[1] + " of " + params[2]);
                return this.boxRowsDeleted;
            });
        } else {
            throw new AssertionError("unexpected statement " + sql);
        }
        return statement;
    }

    private ResultSet rows(boolean present, String column, int value) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.next()).thenReturn(present, false);
        if (column != null) when(set.getInt(column)).thenReturn(value);
        return set;
    }
}
