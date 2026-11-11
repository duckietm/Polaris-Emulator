package polaris.hotel.users.subscriptions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** The history of chosen club gifts: what is written, and that a database error does not throw. */
class ClubGiftClaimLogTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);

    @Test
    void writesWhoTookWhichGiftAndWhen() throws Exception {
        when(this.dataSource.getConnection()).thenReturn(this.connection);
        when(this.connection.prepareStatement(ClubGiftClaimLog.INSERT_SQL)).thenReturn(this.statement);
        when(this.statement.executeUpdate()).thenReturn(1);

        assertTrue(ClubGiftClaimLog.record(this.dataSource, 7, 9001, "club_gift_hc_chr", 1794376576));

        verify(this.statement).setInt(1, 7);
        verify(this.statement).setInt(2, 9001);
        verify(this.statement).setString(3, "club_gift_hc_chr");
        verify(this.statement).setInt(4, 1794376576);
    }

    @Test
    void keepsANameThatIsTooLongToItsFirstPart() throws Exception {
        when(this.dataSource.getConnection()).thenReturn(this.connection);
        when(this.connection.prepareStatement(ClubGiftClaimLog.INSERT_SQL)).thenReturn(this.statement);
        when(this.statement.executeUpdate()).thenReturn(1);

        ClubGiftClaimLog.record(this.dataSource, 7, 9001, "x".repeat(150), 1);

        verify(this.statement).setString(3, "x".repeat(ClubGiftClaimLog.MAX_NAME_LENGTH));
    }

    @Test
    void aDatabaseErrorIsNotThrown() throws Exception {
        when(this.dataSource.getConnection()).thenThrow(new SQLException("down"));

        assertFalse(ClubGiftClaimLog.record(this.dataSource, 7, 9001, "gift", 1));
    }
}
