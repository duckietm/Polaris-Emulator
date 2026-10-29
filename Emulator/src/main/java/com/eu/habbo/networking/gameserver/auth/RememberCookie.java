package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.core.ConfigurationManager;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import java.util.Locale;

/**
 * The remember token as an HttpOnly cookie, for a client that asks for it with {@code "rememberCookie": true}.
 *
 * <p>A token returned in the JSON reply ends up in the browser's storage, where any script running on the client's
 * origin - an ads script, a plugin, injected HTML - can read it and keep the account for 30 days. A client in cookie
 * mode never sees the token: login, remember and refresh set it, remember and refresh read it when the body carries
 * none, logout revokes and expires it. A client or CMS that does not ask keeps the token in the body as before.
 */
final class RememberCookie {
    static final String REQUEST_FLAG = "rememberCookie";
    static final String NAME_SETTING = "login.remember.cookie.name";
    static final String SAME_SITE_SETTING = "login.remember.cookie.samesite";
    static final String SECURE_SETTING = "login.remember.cookie.secure";
    static final String PATH = "/api/auth";

    private RememberCookie() {}

    /** The settings the cookie is written with. {@code sameSite} is Strict unless set to Lax or None. */
    record Settings(String name, CookieHeaderNames.SameSite sameSite, boolean secure) {

        static final Settings DEFAULTS = new Settings("octane_remember", CookieHeaderNames.SameSite.Strict, true);

        static Settings from(ConfigurationManager config) {
            if (config == null) return DEFAULTS;
            String name = config.getValue(NAME_SETTING, DEFAULTS.name()).trim();
            return new Settings(
                    name.isEmpty() ? DEFAULTS.name() : name,
                    sameSite(config.getValue(SAME_SITE_SETTING, "Strict")),
                    config.getBoolean(SECURE_SETTING, true));
        }

        static CookieHeaderNames.SameSite sameSite(String value) {
            return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
                case "lax" -> CookieHeaderNames.SameSite.Lax;
                // A cross-site cookie is only sent over https.
                case "none" -> CookieHeaderNames.SameSite.None;
                default -> CookieHeaderNames.SameSite.Strict;
            };
        }
    }

    /** Whether the client asked to keep the remember token in the cookie. */
    static boolean requested(JsonObject body) {
        return AuthHttpUtil.readBoolean(body, REQUEST_FLAG, false);
    }

    /** The remember token of the request: the body's when it carries one, else the cookie's. */
    static String token(FullHttpRequest req, JsonObject body, Settings settings) {
        String fromBody = AuthHttpUtil.readString(body, "rememberToken").trim();
        if (!fromBody.isEmpty()) return fromBody;
        return fromCookie(req, settings);
    }

    static String fromCookie(FullHttpRequest req, Settings settings) {
        String header = req.headers().get(HttpHeaderNames.COOKIE);
        if (header == null || header.isEmpty()) return "";
        for (Cookie cookie : ServerCookieDecoder.STRICT.decode(header)) {
            if (settings.name().equals(cookie.name()))
                return cookie.value() == null ? "" : cookie.value().trim();
        }
        return "";
    }

    /** The Set-Cookie value that stores {@code jwt} until {@code expiresAt} (epoch seconds). */
    static String issue(String jwt, long expiresAt, long nowSeconds, Settings settings) {
        DefaultCookie cookie = new DefaultCookie(settings.name(), jwt);
        cookie.setHttpOnly(true);
        boolean secure = settings.secure() || settings.sameSite() == CookieHeaderNames.SameSite.None;
        cookie.setSecure(secure);
        cookie.setSameSite(settings.sameSite());
        cookie.setPath(PATH);
        cookie.setMaxAge(Math.max(0, expiresAt - nowSeconds));
        return ServerCookieEncoder.STRICT.encode(cookie);
    }

    /** The Set-Cookie value that removes the cookie. */
    static String expire(Settings settings) {
        DefaultCookie cookie = new DefaultCookie(settings.name(), "");
        cookie.setHttpOnly(true);
        cookie.setSecure(settings.secure() || settings.sameSite() == CookieHeaderNames.SameSite.None);
        cookie.setSameSite(settings.sameSite());
        cookie.setPath(PATH);
        cookie.setMaxAge(0);
        return ServerCookieEncoder.STRICT.encode(cookie);
    }
}
