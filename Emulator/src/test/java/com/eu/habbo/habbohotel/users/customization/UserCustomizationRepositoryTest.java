package com.eu.habbo.habbohotel.users.customization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void nickIconOfferMapsTheEstablishedColumns() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("points")).thenReturn(25);
        when(resultSet.getInt("points_type")).thenReturn(5);
        when(resultSet.getBoolean("enabled")).thenReturn(true);

        var offer =
                new UserCustomizationRepository(dataSource).findNickIcon("star").orElseThrow();

        assertAll(
                () -> assertEquals("star", offer.iconKey()),
                () -> assertEquals(25, offer.points()),
                () -> assertEquals(5, offer.pointsType()),
                () -> assertTrue(offer.enabled()));
        verify(statement).setString(1, "star");
        verify(connection).close();
    }
}
