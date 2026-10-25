package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The password login refused a banned account or address, but remember, refresh and sso-token did
 * not: a banned user holding a remember token went on getting tickets and access tokens for its
 * thirty days.
 */
class SessionBanRefusalTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java");

    private static boolean refuse(AuthHttpUtil.BanInfo accountBan, AuthHttpUtil.BanInfo ipBan) throws Exception {
        Connection connection = mock(Connection.class);

        try (MockedStatic<AuthHttpUtil> util = mockStatic(AuthHttpUtil.class, CALLS_REAL_METHODS);
                MockedStatic<RememberJwtService> remember = mockStatic(RememberJwtService.class);
                MockedStatic<AccessTokenService> access = mockStatic(AccessTokenService.class)) {
            util.when(() -> AuthHttpUtil.lookupAccountBan(any(), anyInt())).thenReturn(accountBan);
            util.when(() -> AuthHttpUtil.lookupIpBan(any(), anyString())).thenReturn(ipBan);
            util.when(() -> AuthHttpUtil.sendJson(any(), any(), any(HttpResponseStatus.class), any(JsonObject.class)))
                    .thenAnswer(i -> null);
            util.when(() -> AuthHttpUtil.bannedPayload(any())).thenReturn(new JsonObject());

            boolean refused = SessionEndpoints.refuseBanned(
                    mock(ChannelHandlerContext.class), mock(FullHttpRequest.class), connection, 7, "10.0.0.9", "jwt");

            if (refused) {
                util.verify(() ->
                        AuthHttpUtil.sendJson(any(), any(), eq(HttpResponseStatus.FORBIDDEN), any(JsonObject.class)));
                remember.verify(() -> RememberJwtService.revokeFromToken(connection, "jwt"));
                access.verify(() -> AccessTokenService.revokeAll(connection, 7));
            } else {
                remember.verify(() -> RememberJwtService.revokeFromToken(any(), anyString()), never());
                access.verify(() -> AccessTokenService.revokeAll(any(), anyInt()), never());
            }
            return refused;
        }
    }

    @Test
    void aBannedAccountIsRefusedAndItsTokensEnd() throws Exception {
        assertTrue(refuse(new AuthHttpUtil.BanInfo("account", "spam", Integer.MAX_VALUE), null));
    }

    @Test
    void aBannedAddressIsRefusedToo() throws Exception {
        assertTrue(refuse(null, new AuthHttpUtil.BanInfo("ip", "spam", Integer.MAX_VALUE)));
    }

    @Test
    void anUnbannedUserGoesThrough() throws Exception {
        assertFalse(refuse(null, null));
    }

    @Test
    void everyTokenEndpointAsks() throws Exception {
        String source = Files.readString(SOURCE);

        int asks = source.split("refuseBanned\\(ctx, req, conn, ", -1).length - 1;
        assertTrue(asks >= 3, "remember, refresh and sso-token each check for a ban:\n" + asks);
        assertTrue(source.contains("refuseBanned(ctx, req, conn, rot.userId, ip, rot.jwt)"));
        assertTrue(source.contains("refuseBanned(ctx, req, conn, userId, ip, null)"));
    }
}
