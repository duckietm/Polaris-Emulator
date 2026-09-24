package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.networking.gameserver.wired.FakeWiredApiRooms.FakeRoom;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Scope;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WiredApiRouterTest {
    private static final String READ = WiredExtraVariableWebApi.mintKey();
    private static final String WRITE = WiredExtraVariableWebApi.mintKey();
    private static final String BASE = "/api/public/rooms/5";

    private final AtomicReference<WiredApiSettings> settings = new AtomicReference<>(WiredApiSettings.defaults(true));
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private FakeWiredApiRooms rooms;
    private FakeRoom room;
    private WiredApiRouter router;
    private String ip = "10.0.0.1";

    @BeforeEach
    void setUp() {
        this.rooms = new FakeWiredApiRooms();
        this.room = this.rooms.room(5, READ, WRITE);
        this.room.variable("points", Scope.USER, true);
        this.room.variable("vip", Scope.USER, false);
        this.room.variable("charge", Scope.FURNI, true);
        this.room.variable("score", Scope.GLOBAL, true);
        this.room.user(1, "alice").user(2, "bob").user(3, "carol").floor(40).wall(41);
        this.room.hold("points", 1, 30);
        this.room.hold("points", 2, 10);
        this.room.hold("points", 3, 20);
        this.room.hold("charge", 40, 7);
        this.router = new WiredApiRouter(this.settings::get, this.rooms, new WiredApiLimits(this.clock::get));
    }

    @Test
    void everyPathAnswersNotFoundWhileDisabled() {
        this.settings.set(WiredApiSettings.defaults(false));

        for (String path : List.of(BASE + "/variables", "/api/public/api-docs", "/api/public/api-docs/")) {
            WiredApiResponse response = this.send("GET", path, READ, null);
            assertEquals(404, response.status());
            assertEquals("disabled", error(response));
        }
        assertEquals(404, this.send("OPTIONS", BASE + "/variables", null, null).status());
    }

    @Test
    void servesTheOpenApiDocumentAndItsPage() {
        WiredApiResponse json = this.send("GET", "/api/public/api-docs", null, null);
        assertEquals(200, json.status());
        JsonObject document = JsonParser.parseString(json.bodyText()).getAsJsonObject();
        assertEquals("3.0.3", document.get("openapi").getAsString());
        JsonObject paths = document.getAsJsonObject("paths");
        for (WiredApiRouter.Route route : WiredApiRouter.ROUTES) {
            if (route.level() != WiredApiAuth.Level.NONE) {
                assertTrue(
                        paths.getAsJsonObject(route.path()).has(route.method().toLowerCase()), route.path());
            }
        }

        WiredApiResponse html = this.send("GET", "/api/public/api-docs/", null, null);
        assertEquals(200, html.status());
        assertTrue(html.headers().get("Content-Type").startsWith("text/html"));
        assertFalse(html.bodyText().contains("<script"));
        assertTrue(html.headers().get("Content-Security-Policy").contains("default-src 'none'"));
        assertTrue(html.bodyText().contains("/rooms/{roomId}/variables/bulk-delete"));
    }

    @Test
    void listsTheRoomsVariables() {
        WiredApiResponse response = this.send("GET", BASE + "/variables", READ, null);

        assertEquals(200, response.status());
        JsonObject body = json(response);
        assertEquals(4, body.getAsJsonArray("variables").size());
        JsonObject first = body.getAsJsonArray("variables").get(0).getAsJsonObject();
        assertEquals("points", first.get("name").getAsString());
        assertEquals("user", first.get("scope").getAsString());
        assertTrue(first.get("hasValue").getAsBoolean());
        assertEquals("60", response.headers().get("X-RateLimit-Limit"));
        assertEquals("59", response.headers().get("X-RateLimit-Remaining"));
        assertNotNull(response.headers().get("X-RateLimit-Reset"));
    }

    @Test
    void readsAndWritesOneHolder() {
        String path = BASE + "/variables/user/points/users/1";
        WiredApiResponse read = this.send("GET", path, WRITE, null);
        assertEquals(200, read.status());
        assertEquals(30, json(read).get("value").getAsInt());
        assertEquals(1, json(read).get("entityId").getAsInt());

        WiredApiResponse put = this.send("PUT", BASE + "/variables/user/points/users/2", WRITE, "{\"value\":99}");
        assertEquals(200, put.status());
        assertEquals(99, json(put).get("value").getAsInt());

        WiredApiResponse set = this.send("PATCH", path, WRITE, "{\"value\":5}");
        assertEquals(5, json(set).get("value").getAsInt());
        WiredApiResponse add = this.send("PATCH", path, WRITE, "{\"add\":-8}");
        assertEquals(-3, json(add).get("value").getAsInt());

        assertEquals(204, this.send("DELETE", path, WRITE, null).status());
        assertEquals(404, this.send("GET", path, READ, null).status());
        assertEquals(404, this.send("PATCH", path, WRITE, "{\"add\":1}").status());
        assertEquals(404, this.send("DELETE", path, WRITE, null).status());
    }

    @Test
    void valueLessVariablesAreCreatedWithoutAValue() {
        String path = BASE + "/variables/user/vip/users/1";

        assertEquals(400, this.send("PUT", path, WRITE, "{\"value\":1}").status());
        WiredApiResponse put = this.send("PUT", path, WRITE, "{}");
        assertEquals(200, put.status());
        assertFalse(json(put).has("value"));
        assertEquals(200, this.send("PATCH", path, WRITE, "{}").status());
        assertEquals(400, this.send("PATCH", path, WRITE, "{\"add\":1}").status());
        assertEquals(
                400,
                this.send("PUT", BASE + "/variables/user/points/users/1", WRITE, "{}")
                        .status());
    }

    @Test
    void writesToAHolderNotInTheRoomAreRefused() {
        WiredApiResponse response = this.send("PUT", BASE + "/variables/user/points/users/77", WRITE, "{\"value\":1}");

        assertEquals(404, response.status());
        assertEquals("not_found", error(response));
    }

    @Test
    void addingPastTheIntRangeIsRefused() {
        this.room.hold("points", 1, Integer.MAX_VALUE);

        WiredApiResponse response = this.send("PATCH", BASE + "/variables/user/points/users/1", WRITE, "{\"add\":1}");

        assertEquals(400, response.status());
        assertEquals(
                Integer.MAX_VALUE,
                this.room
                        .entry(this.room.variables.get(0), WiredApiRooms.TargetKind.USERS, 1)
                        .value());
    }

    @Test
    void pagesSortsAndCountsHolders() {
        String path = BASE + "/variables/user/points/users";

        JsonObject page = json(this.send("GET", path + "?page=1&pageSize=2&sort=value&order=desc", READ, null));
        assertEquals(3, page.get("total").getAsInt());
        assertEquals(2, page.getAsJsonArray("entries").size());
        assertEquals(
                30,
                page.getAsJsonArray("entries")
                        .get(0)
                        .getAsJsonObject()
                        .get("value")
                        .getAsInt());
        assertEquals(
                20,
                page.getAsJsonArray("entries")
                        .get(1)
                        .getAsJsonObject()
                        .get("value")
                        .getAsInt());

        JsonObject second = json(this.send("GET", path + "?page=2&pageSize=2", READ, null));
        assertEquals(1, second.getAsJsonArray("entries").size());
        assertEquals(
                3,
                second.getAsJsonArray("entries")
                        .get(0)
                        .getAsJsonObject()
                        .get("entityId")
                        .getAsInt());

        assertEquals(
                3,
                json(this.send("GET", path + "/count", READ, null)).get("count").getAsInt());
        assertEquals(
                1,
                json(this.send("GET", BASE + "/variables/furni/charge/floor/count", READ, null))
                        .get("count")
                        .getAsInt());
        assertEquals(
                0,
                json(this.send("GET", BASE + "/variables/furni/charge/wall/count", READ, null))
                        .get("count")
                        .getAsInt());
    }

    @Test
    void pageBoundsAreChecked() {
        String path = BASE + "/variables/user/points/users";

        assertEquals(400, this.send("GET", path + "?page=0", READ, null).status());
        assertEquals(400, this.send("GET", path + "?pageSize=101", READ, null).status());
        assertEquals(400, this.send("GET", path + "?pageSize=-1", READ, null).status());
        assertEquals(
                400, this.send("GET", path + "?page=99999999999", READ, null).status());
        assertEquals(400, this.send("GET", path + "?sort=name", READ, null).status());
        assertEquals(400, this.send("GET", path + "?order=up", READ, null).status());
        assertEquals(400, this.send("GET", path + "?limit=5", READ, null).status());
        assertEquals(200, this.send("GET", path + "?page=1000000", READ, null).status());
    }

    @Test
    void bulkDeleteNeedsTheWriteKeyAndThePermission() {
        String body = "{\"names\":[\"points\",\"charge\"]}";
        String path = BASE + "/variables/bulk-delete";

        assertEquals(403, this.send("POST", path, WRITE, body).status());
        this.room.bulk = true;
        assertEquals(403, this.send("POST", path, READ, body).status());

        WiredApiResponse response = this.send("POST", path, WRITE, body);
        assertEquals(200, response.status());
        assertEquals(3, json(response).getAsJsonObject("deleted").get("points").getAsInt());
        assertEquals(1, json(response).getAsJsonObject("deleted").get("charge").getAsInt());
        // Once a minute per room.
        assertEquals(429, this.send("POST", path, WRITE, body).status());
    }

    @Test
    void bulkDeleteChecksEveryNameFirst() {
        this.room.bulk = true;
        String path = BASE + "/variables/bulk-delete";

        assertEquals(
                404,
                this.send("POST", path, WRITE, "{\"names\":[\"points\",\"nope\"]}")
                        .status());
        assertEquals(3, this.room.values.get("points").size());
        assertEquals(
                400,
                this.send("POST", path, WRITE, "{\"names\":[\"points\",\"points\"]}")
                        .status());
        assertEquals(400, this.send("POST", path, WRITE, "{\"names\":[]}").status());
        assertEquals(
                400, this.send("POST", path, WRITE, "{\"names\":[\"a-b\"]}").status());
        StringBuilder many = new StringBuilder("{\"names\":[");
        for (int i = 0; i < 21; i++) {
            many.append(i == 0 ? "" : ",").append("\"v").append(i).append('"');
        }
        assertEquals(
                400,
                this.send("POST", path, WRITE, many.append("]}").toString()).status());
    }

    @Test
    void batchReportsEachOperation() {
        String body = "{\"operations\":["
                + "{\"op\":\"set\",\"targetKind\":\"users\",\"entityId\":1,\"value\":4},"
                + "{\"op\":\"add\",\"targetKind\":\"users\",\"entityId\":2,\"value\":5},"
                + "{\"op\":\"delete\",\"targetKind\":\"users\",\"entityId\":3},"
                + "{\"op\":\"add\",\"targetKind\":\"users\",\"entityId\":77,\"value\":1}]}";

        WiredApiResponse response = this.send("POST", BASE + "/variables/user/points/batch", WRITE, body);

        assertEquals(200, response.status());
        var results = json(response).getAsJsonArray("results");
        assertEquals(4, results.size());
        assertEquals(
                4,
                results.get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("entry")
                        .get("value")
                        .getAsInt());
        assertEquals(
                15,
                results.get(1)
                        .getAsJsonObject()
                        .getAsJsonObject("entry")
                        .get("value")
                        .getAsInt());
        assertTrue(results.get(2).getAsJsonObject().get("ok").getAsBoolean());
        assertFalse(results.get(3).getAsJsonObject().get("ok").getAsBoolean());
        assertEquals(
                "not_found",
                results.get(3)
                        .getAsJsonObject()
                        .getAsJsonObject("error")
                        .get("code")
                        .getAsString());
    }

    @Test
    void batchShapeIsCheckedBeforeAnythingIsWritten() {
        String path = BASE + "/variables/user/points/batch";

        assertEquals(
                400,
                this.send(
                                "POST",
                                path,
                                WRITE,
                                "{\"operations\":[{\"op\":\"drop\",\"targetKind\":\"users\",\"entityId\":1}]}")
                        .status());
        assertEquals(
                400,
                this.send(
                                "POST",
                                path,
                                WRITE,
                                "{\"operations\":[{\"op\":\"set\",\"targetKind\":\"floor\",\"entityId\":1,\"value\":1}]}")
                        .status());
        assertEquals(
                400,
                this.send(
                                "POST",
                                path,
                                WRITE,
                                "{\"operations\":[{\"op\":\"set\",\"targetKind\":\"users\",\"entityId\":0,\"value\":1}]}")
                        .status());
        assertEquals(
                400,
                this.send(
                                "POST",
                                path,
                                WRITE,
                                "{\"operations\":[{\"op\":\"set\",\"targetKind\":\"users\",\"entityId\":1,\"value\":1,\"x\":1}]}")
                        .status());
        assertEquals(400, this.send("POST", path, WRITE, "{\"operations\":[]}").status());
        assertEquals(0, this.room.writes);
        assertEquals(403, this.send("POST", path, READ, "{\"operations\":[]}").status());
    }

    @Test
    void readsAndChangesGlobalVariables() {
        String path = BASE + "/variables/global/score";

        assertEquals(0, json(this.send("GET", path, READ, null)).get("value").getAsInt());
        assertEquals(
                12,
                json(this.send("PATCH", path, WRITE, "{\"value\":12}"))
                        .get("value")
                        .getAsInt());
        assertEquals(
                15,
                json(this.send("PATCH", path, WRITE, "{\"add\":3}"))
                        .get("value")
                        .getAsInt());
        assertEquals(
                400, this.send("PATCH", path, WRITE, "{\"value\":1,\"add\":1}").status());
        assertEquals(400, this.send("PATCH", path, WRITE, "{}").status());
        assertEquals(
                404,
                this.send("GET", BASE + "/variables/global/points", READ, null).status());
    }

    @Test
    void userProfilesByNameIdAndPath() {
        JsonObject byName = json(this.send("GET", BASE + "/variables_profile/user/users?name=alice", READ, null));
        assertEquals(1, byName.get("entityId").getAsInt());
        assertEquals("alice", byName.get("name").getAsString());
        assertEquals(
                30,
                byName.getAsJsonObject("variables")
                        .getAsJsonObject("points")
                        .get("value")
                        .getAsInt());

        JsonObject byId = json(this.send("GET", BASE + "/variables_profile/user/users?unique_id=2", READ, null));
        assertEquals("bob", byId.get("name").getAsString());

        assertEquals(
                400,
                this.send("GET", BASE + "/variables_profile/user/users", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables_profile/user/users?name=a&unique_id=1", READ, null)
                        .status());
        assertEquals(
                404,
                this.send("GET", BASE + "/variables_profile/user/users?name=zed", READ, null)
                        .status());
        assertEquals(
                200,
                this.send("GET", BASE + "/variables_profile/user/users/3", READ, null)
                        .status());
        assertEquals(
                404,
                this.send("GET", BASE + "/variables_profile/user/users/77", READ, null)
                        .status());
    }

    @Test
    void patchesAndDeletesAUserProfile() {
        String path = BASE + "/variables_profile/user/users/1";

        JsonObject patched = json(this.send("PATCH", path, WRITE, "{\"variables\":{\"points\":null,\"vip\":true}}"));
        assertFalse(patched.getAsJsonObject("variables").has("points"));
        assertTrue(patched.getAsJsonObject("variables").has("vip"));

        assertEquals(
                400,
                this.send("PATCH", path, WRITE, "{\"variables\":{\"vip\":3}}").status());
        assertEquals(
                400,
                this.send("PATCH", path, WRITE, "{\"variables\":{\"points\":true}}")
                        .status());
        assertEquals(
                400,
                this.send("PATCH", path, WRITE, "{\"variables\":{\"points\":\"1\"}}")
                        .status());
        assertEquals(
                404,
                this.send("PATCH", path, WRITE, "{\"variables\":{\"charge\":1}}")
                        .status());

        assertEquals(204, this.send("DELETE", path, WRITE, null).status());
        assertEquals(
                0,
                json(this.send("GET", path, READ, null))
                        .getAsJsonObject("variables")
                        .size());
    }

    @Test
    void furniAndGlobalProfiles() {
        JsonObject furni = json(this.send("GET", BASE + "/variables_profile/furni/floor/40", READ, null));
        assertEquals("floor", furni.get("targetKind").getAsString());
        assertEquals(
                7,
                furni.getAsJsonObject("variables")
                        .getAsJsonObject("charge")
                        .get("value")
                        .getAsInt());
        assertFalse(furni.has("name"));
        assertEquals(
                404,
                this.send("GET", BASE + "/variables_profile/furni/wall/40", READ, null)
                        .status());

        JsonObject patched = json(this.send(
                "PATCH", BASE + "/variables_profile/furni/floor/40", WRITE, "{\"variables\":{\"charge\":8}}"));
        assertEquals(
                8,
                patched.getAsJsonObject("variables")
                        .getAsJsonObject("charge")
                        .get("value")
                        .getAsInt());

        JsonObject global =
                json(this.send("PATCH", BASE + "/variables_profile/global", WRITE, "{\"variables\":{\"score\":42}}"));
        assertEquals("global", global.get("targetKind").getAsString());
        assertEquals(5, global.get("entityId").getAsInt());
        assertEquals(
                42,
                global.getAsJsonObject("variables")
                        .getAsJsonObject("score")
                        .get("value")
                        .getAsInt());
        assertEquals(
                400,
                this.send("PATCH", BASE + "/variables_profile/global", WRITE, "{\"variables\":{\"score\":null}}")
                        .status());
        assertEquals(
                200,
                this.send("GET", BASE + "/variables_profile/global", READ, null).status());
    }

    @Test
    void authenticationFailures() {
        String path = BASE + "/variables";

        WiredApiResponse missing = this.send("GET", path, null, null);
        assertEquals(401, missing.status());
        assertEquals("Bearer", missing.headers().get("WWW-Authenticate"));
        assertEquals(
                401,
                this.send("GET", path, WiredExtraVariableWebApi.mintKey(), null).status());
        assertEquals(401, this.send("GET", path, "short", null).status());
        assertEquals(
                401,
                this.request("GET", path, Map.of("authorization", "Basic " + READ), null)
                        .status());
        assertEquals(
                200, this.request("GET", path, Map.of("x-api-key", READ), null).status());
        assertEquals(400, this.send("GET", path + "?key=" + READ, null, null).status());
        assertEquals(400, this.send("GET", path + "?access_token=x", READ, null).status());
        assertEquals(
                403,
                this.send("PUT", BASE + "/variables/user/points/users/1", READ, "{\"value\":1}")
                        .status());
    }

    @Test
    void aKeyOnlyOpensItsOwnRoom() {
        String otherRead = WiredExtraVariableWebApi.mintKey();
        this.rooms.room(6, otherRead, WiredExtraVariableWebApi.mintKey());

        assertEquals(
                401,
                this.send("GET", "/api/public/rooms/6/variables", READ, null).status());
        assertEquals(401, this.send("GET", BASE + "/variables", otherRead, null).status());
        assertEquals(
                401,
                this.send("GET", "/api/public/rooms/404/variables", READ, null).status());
    }

    @Test
    void aBoxOutsideItsOwnersRoomIsInert() {
        this.room.usable = false;

        WiredApiResponse response = this.send("GET", BASE + "/variables", READ, null);

        assertEquals(403, response.status());
        assertEquals("forbidden", error(response));
    }

    @Test
    void anUnloadedRoomIsLoadedOnlyForAMatchingKey() {
        FakeRoom unloaded = new FakeRoom(8, READ, WRITE);
        unloaded.variable("points", Scope.USER, true);
        this.rooms.unloaded.put(8, unloaded);

        assertEquals(
                401,
                this.send("GET", "/api/public/rooms/8/variables", WiredExtraVariableWebApi.mintKey(), null)
                        .status());
        assertEquals(0, this.rooms.loads);

        assertEquals(
                200,
                this.send("GET", "/api/public/rooms/8/variables", READ, null).status());
        assertEquals(1, this.rooms.loads);
        assertEquals(
                200,
                this.send("GET", "/api/public/rooms/8/variables", READ, null).status());
        assertEquals(1, this.rooms.loads);
    }

    @Test
    void pathSegmentsAreValidated() {
        assertEquals(
                400,
                this.send("GET", "/api/public/rooms/0/variables", READ, null).status());
        assertEquals(
                400,
                this.send("GET", "/api/public/rooms/abc/variables", READ, null).status());
        assertEquals(
                400,
                this.send("GET", "/api/public/rooms/2147483648/variables", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", "/api/public/rooms/-5/variables", READ, null).status());
        assertEquals(
                400,
                this.send("GET", "/api/public/rooms/05/variables", READ, null).status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables/user/bad-name/users/1", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables/user/" + "a".repeat(41) + "/users/1", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables/global/score/users/1", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables/room/score/users/1", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables/user/points/floor/1", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables/user/points/users/0", READ, null)
                        .status());
        assertEquals(
                400,
                this.send("GET", BASE + "/variables/user/points/users/9999999999", READ, null)
                        .status());
        assertEquals(
                404,
                this.send("GET", BASE + "/variables/user/nope/users/1", READ, null)
                        .status());
        assertEquals(
                404,
                this.send("GET", BASE + "/variables/furni/points/floor/40", READ, null)
                        .status());
        assertEquals(404, this.send("GET", BASE + "/nothing", READ, null).status());
        assertEquals(404, this.send("GET", BASE + "/variables/", READ, null).status());
        assertEquals(
                404,
                this.send("GET", "/api/public/rooms//variables", READ, null).status());

        WiredApiResponse wrongMethod = this.send("POST", BASE + "/variables", WRITE, "{}");
        assertEquals(405, wrongMethod.status());
        assertEquals("method_not_allowed", error(wrongMethod));
    }

    @Test
    void petsAndBotsDoNotHoldUserVariables() {
        WiredApiResponse pets = this.send("GET", BASE + "/variables/user/points/pets/1", READ, null);
        assertEquals(400, pets.status());
        assertTrue(
                json(pets).getAsJsonObject("error").get("message").getAsString().contains("pets or bots"));
        assertEquals(
                400,
                this.send("GET", BASE + "/variables_profile/user/bots/1", READ, null)
                        .status());
    }

    @Test
    void bodiesAreStrict() {
        String path = BASE + "/variables/user/points/users/1";

        assertEquals(400, this.send("PUT", path, WRITE, "{\"value\":1.5}").status());
        assertEquals(400, this.send("PUT", path, WRITE, "{\"value\":1e3}").status());
        assertEquals(
                400, this.send("PUT", path, WRITE, "{\"value\":2147483648}").status());
        assertEquals(400, this.send("PUT", path, WRITE, "{\"value\":\"1\"}").status());
        assertEquals(
                400, this.send("PUT", path, WRITE, "{\"value\":1,\"value\":2}").status());
        assertEquals(
                400, this.send("PUT", path, WRITE, "{\"value\":1,\"other\":2}").status());
        assertEquals(400, this.send("PUT", path, WRITE, "{\"value\":1} x").status());
        assertEquals(400, this.send("PUT", path, WRITE, "{value:1}").status());
        assertEquals(400, this.send("PUT", path, WRITE, "[1]").status());
        assertEquals(400, this.send("PUT", path, WRITE, "").status());
        assertEquals(400, this.send("PUT", path, WRITE, "{\"value\":1").status());
        assertEquals(400, this.send("GET", path, READ, "{}").status());
        assertEquals(
                400,
                this.request(
                                "PUT",
                                path,
                                Map.of("authorization", "Bearer " + WRITE, "content-type", "text/plain"),
                                "{\"value\":1}")
                        .status());
        assertEquals(
                200, this.send("PUT", path, WRITE, "{\"value\":-2147483648}").status());

        WiredApiResponse big = this.send("PUT", path, WRITE, "{\"value\":1" + " ".repeat(17_000) + "}");
        assertEquals(413, big.status());
        assertEquals("payload_too_large", error(big));
    }

    @Test
    void perAddressLimitAnswersTooManyRequests() {
        this.settings.set(with(3, 60, 200, 10));

        for (int i = 0; i < 3; i++) {
            assertEquals(
                    200, this.send("GET", "/api/public/api-docs", null, null).status());
        }
        WiredApiResponse limited = this.send("GET", "/api/public/api-docs", null, null);
        assertEquals(429, limited.status());
        assertEquals("rate_limited", error(limited));
        assertNotNull(limited.headers().get("Retry-After"));
        assertEquals("3", limited.headers().get("X-RateLimit-Limit"));
        assertEquals("0", limited.headers().get("X-RateLimit-Remaining"));

        this.ip = "10.0.0.2";
        assertEquals(200, this.send("GET", "/api/public/api-docs", null, null).status());
        this.ip = "10.0.0.1";
        this.clock.addAndGet(10_000);
        assertEquals(200, this.send("GET", "/api/public/api-docs", null, null).status());
    }

    @Test
    void perKeyLimitAnswersTooManyRequests() {
        this.settings.set(with(1000, 2, 200, 10));

        assertEquals(200, this.send("GET", BASE + "/variables", READ, null).status());
        assertEquals(200, this.send("GET", BASE + "/variables", READ, null).status());
        assertEquals(429, this.send("GET", BASE + "/variables", READ, null).status());
        assertEquals(200, this.send("GET", BASE + "/variables", WRITE, null).status());
    }

    @Test
    void perRoomWriteLimitCountsEveryWrite() {
        this.settings.set(with(1000, 1000, 3, 10));
        String batch = "{\"operations\":["
                + "{\"op\":\"set\",\"targetKind\":\"users\",\"entityId\":1,\"value\":1},"
                + "{\"op\":\"set\",\"targetKind\":\"users\",\"entityId\":2,\"value\":1}]}";

        assertEquals(
                200,
                this.send("POST", BASE + "/variables/user/points/batch", WRITE, batch)
                        .status());
        assertEquals(
                200,
                this.send("PUT", BASE + "/variables/user/points/users/1", WRITE, "{\"value\":2}")
                        .status());
        WiredApiResponse limited = this.send("PUT", BASE + "/variables/user/points/users/1", WRITE, "{\"value\":3}");
        assertEquals(429, limited.status());
        assertEquals(2, this.room.values.get("points").get(1).value());
        assertEquals(
                200,
                this.send("GET", BASE + "/variables/user/points/users/1", READ, null)
                        .status());
    }

    @Test
    void repeatedFailedAuthenticationBlocksTheAddress() {
        this.settings.set(with(1000, 1000, 200, 2));

        assertEquals(
                401,
                this.send("GET", BASE + "/variables", WiredExtraVariableWebApi.mintKey(), null)
                        .status());
        assertEquals(
                401,
                this.send("GET", BASE + "/variables", WiredExtraVariableWebApi.mintKey(), null)
                        .status());
        assertEquals(
                401,
                this.send("GET", BASE + "/variables", WiredExtraVariableWebApi.mintKey(), null)
                        .status());
        WiredApiResponse blocked = this.send("GET", BASE + "/variables", READ, null);
        assertEquals(429, blocked.status());
        assertNotNull(blocked.headers().get("Retry-After"));

        this.clock.addAndGet(300_001);
        assertEquals(200, this.send("GET", BASE + "/variables", READ, null).status());
    }

    @Test
    void corsPreflightAndConfiguredOrigins() {
        WiredApiResponse preflight =
                this.request("OPTIONS", BASE + "/variables", Map.of("origin", "https://a.test"), null);
        assertEquals(204, preflight.status());
        assertEquals("*", preflight.headers().get("Access-Control-Allow-Origin"));
        assertEquals(
                "Authorization, X-Api-Key, Content-Type", preflight.headers().get("Access-Control-Allow-Headers"));
        assertTrue(preflight.headers().get("Access-Control-Allow-Methods").contains("PATCH"));

        WiredApiSettings d = WiredApiSettings.defaults(true);
        this.settings.set(new WiredApiSettings(
                true,
                d.maxPayloadBytes(),
                true,
                d.perIp(),
                d.perIpWindowMs(),
                d.perKey(),
                d.perKeyWindowMs(),
                d.roomWrites(),
                d.roomWritesWindowMs(),
                d.authFailMax(),
                d.authFailWindowMs(),
                d.authFailBlockMs(),
                d.maxBatch(),
                d.maxBulkNames(),
                d.maxPageSize(),
                List.of("https://good.test")));
        WiredApiResponse denied =
                this.request("OPTIONS", BASE + "/variables", Map.of("origin", "https://evil.test"), null);
        assertNull(denied.headers().get("Access-Control-Allow-Origin"));
        WiredApiResponse allowed = this.request(
                "GET",
                BASE + "/variables",
                Map.of("origin", "https://good.test", "authorization", "Bearer " + READ),
                null);
        assertEquals("https://good.test", allowed.headers().get("Access-Control-Allow-Origin"));
        assertEquals("Origin", allowed.headers().get("Vary"));
        assertTrue(allowed.headers().get("Access-Control-Expose-Headers").contains("Retry-After"));
    }

    @Test
    void errorsNeverEchoTheKey() {
        WiredApiResponse response = this.send("GET", BASE + "/variables?key=" + READ, null, null);

        assertFalse(response.bodyText().contains(READ));
        assertEquals("no-store", response.headers().get("Cache-Control"));
    }

    private WiredApiSettings with(int perIp, int perKey, int roomWrites, int authFailMax) {
        WiredApiSettings d = WiredApiSettings.defaults(true);
        return new WiredApiSettings(
                true,
                d.maxPayloadBytes(),
                true,
                perIp,
                d.perIpWindowMs(),
                perKey,
                d.perKeyWindowMs(),
                roomWrites,
                d.roomWritesWindowMs(),
                authFailMax,
                d.authFailWindowMs(),
                d.authFailBlockMs(),
                d.maxBatch(),
                d.maxBulkNames(),
                d.maxPageSize(),
                d.corsOrigins());
    }

    private WiredApiResponse send(String method, String uri, String key, String body) {
        Map<String, String> headers = new HashMap<>();
        if (key != null) {
            headers.put("authorization", "Bearer " + key);
        }
        if (body != null && !method.equals("GET")) {
            headers.put("content-type", "application/json");
        }
        return this.request(method, uri, headers, body);
    }

    private WiredApiResponse request(String method, String uri, Map<String, String> headers, String body) {
        int question = uri.indexOf('?');
        String path = question < 0 ? uri : uri.substring(0, question);
        Map<String, List<String>> query = new LinkedHashMap<>();
        if (question >= 0) {
            for (String pair : uri.substring(question + 1).split("&")) {
                int equals = pair.indexOf('=');
                query.computeIfAbsent(equals < 0 ? pair : pair.substring(0, equals), k -> new java.util.ArrayList<>())
                        .add(equals < 0 ? "" : pair.substring(equals + 1));
            }
        }
        return this.router.handle(new WiredApiRequest(
                method,
                path,
                query,
                new HashMap<>(headers),
                body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8),
                this.ip));
    }

    private static JsonObject json(WiredApiResponse response) {
        return JsonParser.parseString(response.bodyText()).getAsJsonObject();
    }

    private static String error(WiredApiResponse response) {
        return json(response).getAsJsonObject("error").get("code").getAsString();
    }
}
