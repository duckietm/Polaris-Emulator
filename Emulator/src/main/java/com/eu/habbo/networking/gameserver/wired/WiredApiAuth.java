package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi.Access;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks the keys of a request against the web-api box of the requested room. Like Habbo, the read
 * key goes in {@code X-Wired-Read-Key} and the write key in {@code X-Wired-Write-Key}; the older
 * {@code Authorization: Bearer} and {@code X-Api-Key} still carry either key. Every key sent must be
 * valid, and the write header only takes the write key; the write key also opens reads. Keys are only
 * ever hashed and compared as hashes; they are never logged or echoed. A room that is not loaded is
 * only loaded once its stored box has matched the keys, and that load counts against the key's limit.
 */
final class WiredApiAuth {
    static final String READ_KEY_HEADER = "x-wired-read-key";
    static final String WRITE_KEY_HEADER = "x-wired-write-key";

    enum Level {
        NONE,
        READ,
        WRITE,
        BULK
    }

    record Session(WiredApiRooms.VariableRoom room, Access access, WiredApiLimits.Window window) {
        boolean canWrite() {
            return this.access == Access.WRITE;
        }
    }

    /** A key the request carries; {@code writeOnly} when it came in the write header. */
    private record Presented(byte[] hash, boolean writeOnly) {}

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
            "read-key",
            "write-key",
            "authorization",
            "x-api-key",
            READ_KEY_HEADER,
            WRITE_KEY_HEADER);

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
                throw WiredApiException.badRequest("API keys are not accepted in the URL; send them in a header.");
            }
        }
    }

    /** The key of the older Polaris headers: {@code Authorization: Bearer}, else {@code X-Api-Key}. */
    static String legacyKey(WiredApiRequest request) {
        String authorization = request.header("authorization");
        if (authorization != null
                && authorization.length() > 7
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return trimmed(request.header("x-api-key"));
    }

    private static String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    Session authenticate(WiredApiRequest request, int roomId, Level level, WiredApiSettings settings) {
        List<Presented> keys = this.presented(request, settings);

        WiredApiRooms.VariableRoom room = this.rooms.loadedRoom(roomId);
        WiredApiLimits.Window window;
        Access access;
        if (room != null) {
            access = this.check(request, settings, keys, room::authenticate);
            window = this.limits.acquireKey(keyId(roomId, keys), settings);
        } else {
            boolean stored = false;
            for (WiredExtraVariableWebApi.KeyState box : this.rooms.storedKeys(roomId)) {
                stored |= matches(keys, box::authenticate) != null;
            }
            if (!stored) {
                throw this.failed(request, settings);
            }
            window = this.limits.acquireKey(keyId(roomId, keys), settings);
            room = this.rooms.loadRoom(roomId);
            if (room == null) {
                throw this.failed(request, settings);
            }
            access = this.check(request, settings, keys, room::authenticate);
        }

        if (!room.boxUsable()) {
            throw WiredApiException.forbidden(
                    WiredApiException.API_DISABLED,
                    "The Variables Web API box only works in a room owned by the box owner.");
        }
        if ((level == Level.WRITE || level == Level.BULK) && access != Access.WRITE) {
            throw WiredApiException.keyMissing();
        }
        if (level == Level.BULK && !room.bulkDeleteAllowed()) {
            throw WiredApiException.forbidden(
                    WiredApiException.BULK_DELETE_NOT_ENABLED, "Bulk delete is not enabled on this box.");
        }
        return new Session(room, access, window);
    }

    private List<Presented> presented(WiredApiRequest request, WiredApiSettings settings) {
        String readKey = trimmed(request.header(READ_KEY_HEADER));
        String writeKey = trimmed(request.header(WRITE_KEY_HEADER));
        String legacyKey = legacyKey(request);
        if (readKey == null && writeKey == null && legacyKey == null) {
            this.limits.recordAuthFailure(request.clientIp(), settings);
            throw WiredApiException.keyMissing();
        }
        List<Presented> keys = new ArrayList<>(3);
        this.add(keys, writeKey, true, request, settings);
        this.add(keys, readKey, false, request, settings);
        this.add(keys, legacyKey, false, request, settings);
        return keys;
    }

    private void add(
            List<Presented> keys, String key, boolean writeOnly, WiredApiRequest request, WiredApiSettings settings) {
        if (key == null) {
            return;
        }
        byte[] hash = WiredExtraVariableWebApi.hashKey(key);
        if (hash == null) {
            throw this.failed(request, settings);
        }
        keys.add(new Presented(hash, writeOnly));
    }

    private interface KeyCheck {
        Access open(byte[] hash);
    }

    /** What all the keys open together, or null when any of them is not a key of this box. */
    private static Access matches(List<Presented> keys, KeyCheck box) {
        Access best = null;
        for (Presented key : keys) {
            Access opened = box.open(key.hash());
            if (opened == null || (key.writeOnly() && opened != Access.WRITE)) {
                return null;
            }
            if (best == null || opened == Access.WRITE) {
                best = opened;
            }
        }
        return best;
    }

    private Access check(WiredApiRequest request, WiredApiSettings settings, List<Presented> keys, KeyCheck box) {
        Access access = matches(keys, box);
        if (access == null) {
            throw this.failed(request, settings);
        }
        return access;
    }

    /** The limiter key: the room and a hash prefix of the first key sent (write header, read header, legacy). */
    private static String keyId(int roomId, List<Presented> keys) {
        return roomId + ":" + HexFormat.of().formatHex(keys.get(0).hash(), 0, 16);
    }

    private WiredApiException failed(WiredApiRequest request, WiredApiSettings settings) {
        this.limits.recordAuthFailure(request.clientIp(), settings);
        return WiredApiException.keyInvalid();
    }
}
