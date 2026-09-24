package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Checks the key of a request against the web-api box of the requested room. The key is only ever
 * hashed and compared as a hash; it is never logged or echoed. A room that is not loaded is only
 * loaded once its stored box has matched the key, and that load counts against the key's limit.
 */
final class WiredApiAuth {
    enum Level {
        NONE,
        READ,
        WRITE,
        BULK
    }

    record Session(
            WiredApiRooms.VariableRoom room, WiredExtraVariableWebApi.Access access, WiredApiLimits.Window window) {}

    private static final Set<String> KEY_QUERY_NAMES = Set.of(
            "key",
            "apikey",
            "api_key",
            "api-key",
            "access_token",
            "token",
            "readkey",
            "writekey",
            "read_key",
            "write_key",
            "authorization",
            "x-api-key");

    private final WiredApiRooms rooms;
    private final WiredApiLimits limits;

    WiredApiAuth(WiredApiRooms rooms, WiredApiLimits limits) {
        this.rooms = rooms;
        this.limits = limits;
    }

    /** Refuses a key carried in the query string, so it does not end up in proxy logs. */
    static void refuseKeyInQuery(WiredApiRequest request) {
        for (String name : request.query().keySet()) {
            if (KEY_QUERY_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                throw WiredApiException.badRequest(
                        "API keys are not accepted in the URL; send them in the Authorization header.");
            }
        }
    }

    static String presentedKey(WiredApiRequest request) {
        String authorization = request.header("authorization");
        if (authorization != null) {
            if (authorization.length() < 7 || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return null;
            }
            return authorization.substring(7).trim();
        }
        String apiKey = request.header("x-api-key");
        return apiKey == null ? null : apiKey.trim();
    }

    Session authenticate(WiredApiRequest request, int roomId, Level level, WiredApiSettings settings) {
        byte[] hash = WiredExtraVariableWebApi.hashKey(presentedKey(request));
        if (hash == null) {
            throw this.failed(request, settings);
        }
        String keyId = roomId + ":" + HexFormat.of().formatHex(hash, 0, 16);

        WiredApiRooms.VariableRoom room = this.rooms.loadedRoom(roomId);
        WiredExtraVariableWebApi.Access access;
        WiredApiLimits.Window window;
        if (room != null) {
            access = room.authenticate(hash);
            if (access == null) {
                throw this.failed(request, settings);
            }
            window = this.limits.acquireKey(keyId, settings);
        } else {
            boolean stored = false;
            for (WiredExtraVariableWebApi.KeyState keys : this.rooms.storedKeys(roomId)) {
                stored |= keys.authenticate(hash) != null;
            }
            if (!stored) {
                throw this.failed(request, settings);
            }
            window = this.limits.acquireKey(keyId, settings);
            room = this.rooms.loadRoom(roomId);
            access = room == null ? null : room.authenticate(hash);
            if (access == null) {
                throw this.failed(request, settings);
            }
        }

        if (!room.boxUsable()) {
            throw WiredApiException.forbidden("The Variables Web API box only works in a room owned by the box owner.");
        }
        if ((level == Level.WRITE || level == Level.BULK) && access != WiredExtraVariableWebApi.Access.WRITE) {
            throw WiredApiException.forbidden("This endpoint needs the write key.");
        }
        if (level == Level.BULK && !room.bulkDeleteAllowed()) {
            throw WiredApiException.forbidden("Bulk delete is not enabled on this box.");
        }
        return new Session(room, access, window);
    }

    private WiredApiException failed(WiredApiRequest request, WiredApiSettings settings) {
        this.limits.recordAuthFailure(request.clientIp(), settings);
        return WiredApiException.unauthorized();
    }
}
