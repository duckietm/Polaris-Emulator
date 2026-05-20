package com.eu.habbo.networking.gameserver.badges;

import com.eu.habbo.Emulator;
import com.eu.habbo.networking.gameserver.auth.AccessTokenService;
import com.eu.habbo.networking.gameserver.auth.CorsOriginGate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class BadgeLeaderboardHttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeLeaderboardHttpHandler.class);

    private static final String BASE_PATH = "/api/badges/leaderboard";
    private static final long CACHE_TTL_MS = 15_000L;
    private static final int MAX_BOARD_USERS = 100;

    private static volatile Snapshot cache = null;

    private static final class Snapshot {
        final List<UserBadgeAggregate> badgeUsers;
        final List<UserAchievementAggregate> achievementUsers;
        final JsonArray badgeStats;
        final long expiresAt;

        Snapshot(List<UserBadgeAggregate> badgeUsers, List<UserAchievementAggregate> achievementUsers, JsonArray badgeStats, long expiresAt) {
            this.badgeUsers = badgeUsers;
            this.achievementUsers = achievementUsers;
            this.badgeStats = badgeStats;
            this.expiresAt = expiresAt;
        }
    }

    private static final class UserBadgeAggregate {
        final int userId;
        final String username;
        final String figure;
        final int totalBadges;
        final EnumMap<Rarity, Integer> counts;

        UserBadgeAggregate(int userId, String username, String figure, int totalBadges, EnumMap<Rarity, Integer> counts) {
            this.userId = userId;
            this.username = username;
            this.figure = figure;
            this.totalBadges = totalBadges;
            this.counts = counts;
        }
    }

    private static final class UserAchievementAggregate {
        final int userId;
        final String username;
        final String figure;
        final int achievementScore;

        UserAchievementAggregate(int userId, String username, String figure, int achievementScore) {
            this.userId = userId;
            this.username = username;
            this.figure = figure;
            this.achievementScore = achievementScore;
        }
    }

    private static final class ViewerProfile {
        final int userId;
        final String username;
        final String figure;

        ViewerProfile(int userId, String username, String figure) {
            this.userId = userId;
            this.username = username;
            this.figure = figure;
        }
    }

    private enum Rarity {
        COMMON("common"),
        RARE("rare"),
        EPIC("epic"),
        LEGENDARY("legendary"),
        MYTHICAL("mythical"),
        UNIQUE("unique");

        final String key;

        Rarity(String key) {
            this.key = key;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!path.equals(BASE_PATH) && !path.startsWith(BASE_PATH + "/")) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        if (req.method() != HttpMethod.GET && req.method() != HttpMethod.HEAD) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use GET."));
            return;
        }

        try {
            Snapshot snapshot = loadSnapshot();
            int viewerUserId = authenticateOptional(req);
            ViewerProfile viewerProfile = loadViewerProfile(viewerUserId);

            JsonObject payload = new JsonObject();
            payload.addProperty("viewerUserId", viewerUserId);
            payload.add("badgeStats", cloneArray(snapshot.badgeStats));
            payload.add("thresholds", buildThresholdsPayload());

            JsonObject boards = new JsonObject();
            boards.add("totalBadges", buildBadgeBoard(snapshot.badgeUsers, viewerUserId, viewerProfile, null));
            boards.add("achievementLevel", buildAchievementBoard(snapshot.achievementUsers, viewerUserId, viewerProfile));

            JsonObject rarityBoards = new JsonObject();
            for (Rarity rarity : Rarity.values()) {
                rarityBoards.add(rarity.key, buildBadgeBoard(snapshot.badgeUsers, viewerUserId, viewerProfile, rarity));
            }

            boards.add("rarity", rarityBoards);
            payload.add("leaderboards", boards);

            sendJson(ctx, req, HttpResponseStatus.OK, payload);
        } catch (Exception e) {
            LOGGER.error("[badges/leaderboard] unexpected error", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error."));
        }
    }

    private Snapshot loadSnapshot() throws Exception {
        long now = System.currentTimeMillis();
        Snapshot current = cache;
        if (current != null && current.expiresAt >= now) return current;

        synchronized (BadgeLeaderboardHttpHandler.class) {
            current = cache;
            if (current != null && current.expiresAt >= now) return current;

            JsonArray badgeStats = new JsonArray();
            List<UserBadgeAggregate> badgeUsers = new ArrayList<>();
            List<UserAchievementAggregate> achievementUsers = new ArrayList<>();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                loadBadgeStats(connection, badgeStats);
                loadBadgeUsers(connection, badgeUsers);
                loadAchievementUsers(connection, achievementUsers);
            }

            Snapshot built = new Snapshot(badgeUsers, achievementUsers, badgeStats, now + CACHE_TTL_MS);
            cache = built;
            return built;
        }
    }

    private void loadBadgeStats(Connection connection, JsonArray badgeStats) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT badge_code, COUNT(DISTINCT user_id) AS owner_count " +
                        "FROM users_badges GROUP BY badge_code ORDER BY owner_count ASC, badge_code ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    String badgeCode = set.getString("badge_code");
                    int ownerCount = set.getInt("owner_count");

                    JsonObject entry = new JsonObject();
                    entry.addProperty("badgeCode", badgeCode);
                    entry.addProperty("ownerCount", ownerCount);
                    entry.addProperty("rarity", classify(ownerCount).key);
                    badgeStats.add(entry);
                }
            }
        }
    }

    private void loadBadgeUsers(Connection connection, List<UserBadgeAggregate> badgeUsers) throws Exception {
        String sql =
                "SELECT u.id AS user_id, u.username, u.look, " +
                        "COUNT(DISTINCT ub.badge_code) AS total_badges, " +
                        "COUNT(DISTINCT CASE WHEN counts.owner_count > 50 THEN ub.badge_code END) AS common_count, " +
                        "COUNT(DISTINCT CASE WHEN counts.owner_count > 10 AND counts.owner_count <= 50 THEN ub.badge_code END) AS rare_count, " +
                        "COUNT(DISTINCT CASE WHEN counts.owner_count > 6 AND counts.owner_count <= 10 THEN ub.badge_code END) AS epic_count, " +
                        "COUNT(DISTINCT CASE WHEN counts.owner_count > 3 AND counts.owner_count <= 6 THEN ub.badge_code END) AS legendary_count, " +
                        "COUNT(DISTINCT CASE WHEN counts.owner_count > 1 AND counts.owner_count <= 3 THEN ub.badge_code END) AS mythical_count, " +
                        "COUNT(DISTINCT CASE WHEN counts.owner_count = 1 THEN ub.badge_code END) AS unique_count " +
                        "FROM users_badges ub " +
                        "INNER JOIN users u ON u.id = ub.user_id " +
                        "INNER JOIN (SELECT badge_code, COUNT(DISTINCT user_id) AS owner_count FROM users_badges GROUP BY badge_code) counts ON counts.badge_code = ub.badge_code " +
                        "GROUP BY u.id, u.username, u.look";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    EnumMap<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
                    counts.put(Rarity.COMMON, set.getInt("common_count"));
                    counts.put(Rarity.RARE, set.getInt("rare_count"));
                    counts.put(Rarity.EPIC, set.getInt("epic_count"));
                    counts.put(Rarity.LEGENDARY, set.getInt("legendary_count"));
                    counts.put(Rarity.MYTHICAL, set.getInt("mythical_count"));
                    counts.put(Rarity.UNIQUE, set.getInt("unique_count"));

                    badgeUsers.add(new UserBadgeAggregate(
                            set.getInt("user_id"),
                            safe(set.getString("username")),
                            safe(set.getString("look")),
                            set.getInt("total_badges"),
                            counts
                    ));
                }
            }
        }
    }

    private void loadAchievementUsers(Connection connection, List<UserAchievementAggregate> achievementUsers) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT u.id AS user_id, u.username, u.look, COALESCE(us.achievement_score, 0) AS achievement_score " +
                        "FROM users u INNER JOIN users_settings us ON us.user_id = u.id " +
                        "WHERE COALESCE(us.achievement_score, 0) > 0")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    achievementUsers.add(new UserAchievementAggregate(
                            set.getInt("user_id"),
                            safe(set.getString("username")),
                            safe(set.getString("look")),
                            set.getInt("achievement_score")
                    ));
                }
            }
        }
    }

    private ViewerProfile loadViewerProfile(int viewerUserId) throws Exception {
        if (viewerUserId <= 0) return null;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, username, look FROM users WHERE id = ? LIMIT 1")) {
            statement.setInt(1, viewerUserId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return null;

                return new ViewerProfile(
                        set.getInt("id"),
                        safe(set.getString("username")),
                        safe(set.getString("look"))
                );
            }
        }
    }

    private JsonObject buildBadgeBoard(List<UserBadgeAggregate> users, int viewerUserId, ViewerProfile viewerProfile, Rarity rarity) {
        List<JsonObject> ranked = new ArrayList<>();

        for (UserBadgeAggregate user : users) {
            int score = (rarity == null) ? user.totalBadges : user.counts.getOrDefault(rarity, 0);
            if (score <= 0) continue;
            ranked.add(toEntry(user.userId, user.username, user.figure, score));
        }

        ranked.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.get("score").getAsInt(), a.get("score").getAsInt());
            if (scoreCompare != 0) return scoreCompare;
            return Integer.compare(a.get("userId").getAsInt(), b.get("userId").getAsInt());
        });

        return finalizeBoard(ranked, viewerUserId, viewerProfile);
    }

    private JsonObject buildAchievementBoard(List<UserAchievementAggregate> users, int viewerUserId, ViewerProfile viewerProfile) {
        List<JsonObject> ranked = new ArrayList<>();

        for (UserAchievementAggregate user : users) {
            if (user.achievementScore <= 0) continue;
            ranked.add(toEntry(user.userId, user.username, user.figure, user.achievementScore));
        }

        ranked.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.get("score").getAsInt(), a.get("score").getAsInt());
            if (scoreCompare != 0) return scoreCompare;
            return Integer.compare(a.get("userId").getAsInt(), b.get("userId").getAsInt());
        });

        return finalizeBoard(ranked, viewerUserId, viewerProfile);
    }

    private JsonObject finalizeBoard(List<JsonObject> ranked, int viewerUserId, ViewerProfile viewerProfile) {
        JsonArray entries = new JsonArray();
        JsonObject viewerEntry = null;

        int cappedSize = Math.min(ranked.size(), MAX_BOARD_USERS);

        for (int index = 0; index < cappedSize; index++) {
            JsonObject entry = ranked.get(index).deepCopy();
            int rank = index + 1;
            entry.addProperty("rank", rank);

            entries.add(entry);
            if (viewerUserId > 0 && entry.get("userId").getAsInt() == viewerUserId) viewerEntry = entry;
        }

        if (viewerEntry == null && viewerUserId > 0) {
            for (int index = 0; index < ranked.size(); index++) {
                JsonObject entry = ranked.get(index);

                if (entry.get("userId").getAsInt() != viewerUserId) continue;

                viewerEntry = entry.deepCopy();
                viewerEntry.addProperty("rank", index + 1);
                break;
            }
        }

        if (viewerEntry == null && viewerProfile != null) {
            viewerEntry = toEntry(viewerProfile.userId, viewerProfile.username, viewerProfile.figure, 0);
            viewerEntry.addProperty("rank", 0);
        }

        JsonObject board = new JsonObject();
        board.add("entries", entries);
        board.addProperty("totalPlayers", cappedSize);
        board.add("viewerEntry", viewerEntry != null ? viewerEntry : new JsonObject());
        return board;
    }

    private JsonObject toEntry(int userId, String username, String figure, int score) {
        JsonObject entry = new JsonObject();
        entry.addProperty("userId", userId);
        entry.addProperty("username", username);
        entry.addProperty("figure", figure);
        entry.addProperty("score", score);
        return entry;
    }

    private JsonObject buildThresholdsPayload() {
        JsonObject thresholds = new JsonObject();
        thresholds.addProperty("commonMinOwners", 51);
        thresholds.addProperty("rareMinOwners", 11);
        thresholds.addProperty("epicMinOwners", 7);
        thresholds.addProperty("legendaryMinOwners", 4);
        thresholds.addProperty("mythicalMinOwners", 2);
        thresholds.addProperty("uniqueOwners", 1);
        return thresholds;
    }

    private static Rarity classify(int ownerCount) {
        if (ownerCount > 50) return Rarity.COMMON;
        if (ownerCount > 10) return Rarity.RARE;
        if (ownerCount > 6) return Rarity.EPIC;
        if (ownerCount > 3) return Rarity.LEGENDARY;
        if (ownerCount > 1) return Rarity.MYTHICAL;
        if (ownerCount > 0) return Rarity.UNIQUE;
        return Rarity.COMMON;
    }

    private static int authenticateOptional(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (header == null || header.isEmpty()) return 0;

        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        return AccessTokenService.verify(token);
    }

    private static JsonArray cloneArray(JsonArray source) {
        JsonArray copy = new JsonArray();
        source.forEach(element -> copy.add(element.deepCopy()));
        return copy;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static JsonObject error(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return obj;
    }

    private static void sendJson(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, JsonObject body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);

        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Credentials", "true");
        }

        response.headers().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");

        String requestedHeaders = req.headers().get("Access-Control-Request-Headers");
        if (requestedHeaders != null && !requestedHeaders.isEmpty()) {
            response.headers().set("Access-Control-Allow-Headers", requestedHeaders);
        } else {
            response.headers().set("Access-Control-Allow-Headers",
                    "Authorization, Content-Type, X-Requested-With, X-Nitro-Key, X-Nitro-Api");
        }

        response.headers().set("Vary", "Origin, Access-Control-Request-Headers, Access-Control-Request-Method");
        response.headers().set("Access-Control-Max-Age", "600");
        response.headers().set("Access-Control-Expose-Headers", "X-Nitro-Sec, X-Nitro-Key-Fp, X-Nitro-Derive-Fp");
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        return connection == null || !"close".equalsIgnoreCase(connection);
    }
}
