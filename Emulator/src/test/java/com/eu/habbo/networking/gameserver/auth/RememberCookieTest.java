package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import org.junit.jupiter.api.Test;

/**
 * The remember token went back in the JSON reply only, so the client kept it in localStorage where any script on its
 * origin could read it. A client that asks for cookie mode now gets it as an HttpOnly cookie and never sees it.
 */
class RememberCookieTest {

    private static final RememberCookie.Settings SETTINGS = RememberCookie.Settings.DEFAULTS;

    private static FullHttpRequest request(String cookieHeader) {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/auth/remember");
        if (cookieHeader != null) req.headers().set(HttpHeaderNames.COOKIE, cookieHeader);
        return req;
    }

    private static JsonObject body(String token, boolean cookieMode) {
        JsonObject body = new JsonObject();
        if (token != null) body.addProperty("rememberToken", token);
        if (cookieMode) body.addProperty(RememberCookie.REQUEST_FLAG, true);
        return body;
    }

    @Test
    void theCookieIsHttpOnlySecureStrictAndLimitedToTheAuthApi() {
        String header = RememberCookie.issue("jwt.value.sig", 1_000_100, 1_000_000, SETTINGS);

        assertTrue(header.startsWith("octane_remember=jwt.value.sig"), header);
        assertTrue(header.contains("HTTPOnly") || header.contains("HttpOnly"), header);
        assertTrue(header.contains("Secure"), header);
        assertTrue(header.contains("SameSite=Strict"), header);
        assertTrue(header.contains("Path=/api/auth"), header);
        assertTrue(header.contains("Max-Age=100"), header);
    }

    @Test
    void aCrossSiteCookieIsAlwaysSecure() {
        RememberCookie.Settings crossSite =
                new RememberCookie.Settings("octane_remember", CookieHeaderNames.SameSite.None, false);

        String header = RememberCookie.issue("jwt", 2, 1, crossSite);

        assertTrue(header.contains("SameSite=None"), header);
        assertTrue(header.contains("Secure"), header);
    }

    @Test
    void sameSiteFallsBackToStrict() {
        assertEquals(CookieHeaderNames.SameSite.Lax, RememberCookie.Settings.sameSite(" lax "));
        assertEquals(CookieHeaderNames.SameSite.None, RememberCookie.Settings.sameSite("None"));
        assertEquals(CookieHeaderNames.SameSite.Strict, RememberCookie.Settings.sameSite("whatever"));
        assertEquals(CookieHeaderNames.SameSite.Strict, RememberCookie.Settings.sameSite(null));
    }

    @Test
    void theBodyTokenWinsAndTheCookieIsReadWhenTheBodyHasNone() {
        FullHttpRequest req = request("other=1; octane_remember=from-cookie");

        assertEquals("from-body", RememberCookie.token(req, body("from-body", false), SETTINGS));
        assertEquals("from-cookie", RememberCookie.token(req, body(null, true), SETTINGS));
        assertEquals("", RememberCookie.token(request(null), body(null, true), SETTINGS));
        req.release();
    }

    @Test
    void expiringTheCookieEmptiesItAtOnce() {
        String header = RememberCookie.expire(SETTINGS);

        assertTrue(header.startsWith("octane_remember="), header);
        assertTrue(header.contains("Max-Age=0"), header);
        assertTrue(header.contains("Path=/api/auth"), header);
    }

    @Test
    void inCookieModeTheTokenNeverReachesTheReply() {
        RememberJwtService.RotationResult remembered =
                new RememberJwtService.RotationResult("jwt.secret", 7, "simo", 4_102_444_800L);

        JsonObject cookieReply = new JsonObject();
        String setCookie = SessionEndpoints.addRemember(cookieReply, remembered, true);
        assertFalse(cookieReply.has("rememberToken"));
        assertEquals(4_102_444_800L, cookieReply.get("rememberExpiresAt").getAsLong());
        assertTrue(setCookie.startsWith("octane_remember=jwt.secret"), setCookie);

        JsonObject bodyReply = new JsonObject();
        assertNull(
                SessionEndpoints.addRemember(bodyReply, remembered, false), "a client that did not ask gets no cookie");
        assertEquals("jwt.secret", bodyReply.get("rememberToken").getAsString());
    }
}
