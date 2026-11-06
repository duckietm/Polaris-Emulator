package polaris.hotel.users.customization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class UserCustomizationRepositoryTest {

    @Test
    void catalogPrefixOfferMapsTheEstablishedColumns() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("display_name")).thenReturn("Gold");
        when(resultSet.getString("text")).thenReturn("VIP");
        when(resultSet.getString("color")).thenReturn("#FFD700");
        when(resultSet.getString("icon")).thenReturn("star");
        when(resultSet.getString("effect")).thenReturn("glow");
        when(resultSet.getString("font")).thenReturn("pixel");
        when(resultSet.getInt("points")).thenReturn(25);
        when(resultSet.getInt("points_type")).thenReturn(5);

        var offer = new UserCustomizationRepository(dataSource)
                .findCatalogPrefix(17)
                .orElseThrow();

        assertAll(
                () -> assertEquals(17, offer.id()),
                () -> assertEquals("Gold", offer.displayName()),
                () -> assertEquals("VIP", offer.text()),
                () -> assertEquals("#FFD700", offer.color()),
                () -> assertEquals("star", offer.icon()),
                () -> assertEquals("glow", offer.effect()),
                () -> assertEquals("pixel", offer.font()),
                () -> assertEquals(25, offer.points()),
                () -> assertEquals(5, offer.pointsType()));
        verify(statement).setInt(1, 17);
        verify(connection).close();
    }

    @Test
    void malformedPrefixSettingKeepsCallerDefaultsAvailable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("key_name")).thenReturn("max_length", "price_credits");
        when(resultSet.getString("value")).thenReturn("invalid", "12");

        var settings = new UserCustomizationRepository(dataSource).loadPrefixSettings();

        assertAll(
                () -> assertEquals(12, settings.get("price_credits")),
                () -> assertFalse(settings.containsKey("max_length")));
    }
}
