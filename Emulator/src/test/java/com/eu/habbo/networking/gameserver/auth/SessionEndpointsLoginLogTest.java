package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariDataSource;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

/** A failed login is logged without any part of the stored password, whatever format a hotel stored it in. */
class SessionEndpointsLoginLogTest {

    @Test
    void aMismatchOnALegacyPasswordDoesNotLogItsBeginning() throws Exception {
        String log = this.failedLoginLog("hunter2-in-plain-text");

        assertTrue(log.contains("password mismatch for user id=7"), log);
        assertFalse(log.contains("hunter2"), log);
    }

    @Test
    void aMismatchOnABcryptHashDoesNotLogItsBeginning() throws Exception {
        String log = this.failedLoginLog("$2a$12$abcdefghijklmnopqrstuuJ1V8Q0lW6zE6vC1oE4mJ6m2xX3y4z5a");

        assertTrue(log.contains("password mismatch for user id=7"), log);
        assertFalse(log.contains("$2a$12$"), log);
    }

    private String failedLoginLog(String stored) throws Exception {
        ResultSet user = mock(ResultSet.class);
        when(user.next()).thenReturn(true);
        when(user.getInt("id")).thenReturn(7);
        when(user.getString("password")).thenReturn(stored);
        when(user.getString("username")).thenReturn("ann");
        PreparedStatement select = mock(PreparedStatement.class);
        when(select.executeQuery()).thenReturn(user);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);

        JsonObject body = new JsonObject();
        body.addProperty("username", "ann");
        body.addProperty("password", "wrong-password");

        Logger logger = (Logger) LoggerFactory.getLogger(SessionEndpoints.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<AuthHttpUtil> util = mockStatic(AuthHttpUtil.class, CALLS_REAL_METHODS);
                MockedStatic<AuthRateLimiter> limiter = mockStatic(AuthRateLimiter.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            util.when(() -> AuthHttpUtil.lookupIpBan(any(), anyString())).thenReturn(null);
            util.when(() -> AuthHttpUtil.sendJson(any(), any(), any(HttpResponseStatus.class), any(JsonObject.class)))
                    .thenAnswer(i -> null);

            SessionEndpoints.handleLogin(
                    mock(ChannelHandlerContext.class), mock(FullHttpRequest.class), body, "10.0.0.9");

            util.verify(() ->
                    AuthHttpUtil.sendJson(any(), any(), eq(HttpResponseStatus.UNAUTHORIZED), any(JsonObject.class)));
        } finally {
            logger.detachAppender(events);
        }

        List<String> messages =
                events.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
        return String.join("\n", messages);
    }
}
