package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.networking.gameserver.wired.FakeWiredApiRooms.FakeRoom;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Scope;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.TargetKind;
import com.google.gson.JsonArray;
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

/** The API against Habbo's Wired Variables contract (habbo-sdk), plus the older Polaris inputs. */
class WiredApiRouterTest {
    private static final String READ = WiredExtraVariableWebApi.mintKey();
    private static final String WRITE = WiredExtraVariableWebApi.mintKey();
    private static final String BASE = "/api/public/rooms/5";
    /** The fake room's timestamps: 1000 unix seconds. */
    private static final String TIME = "1970-01-01T00:16:40Z";

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
            assertError(response, 404, "wired.variables.unknown_endpoint");
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
        JsonObject schemes = document.getAsJsonObject("components").getAsJsonObject("securitySchemes");
        assertEquals(
                "X-Wired-Read-Key",
                schemes.getAsJsonObject("readKey").get("name").getAsString());
        assertEquals(
                "X-Wired-Write-Key",
                schemes.getAsJsonObject("writeKey").get("name").getAsString());

        WiredApiResponse html = this.send("GET", "/api/public/api-docs/", null, null);
        assertEquals(200, html.status());
        assertTrue(html.headers().get("Content-Type").startsWith("text/html"));
        assertFalse(html.bodyText().contains("<script"));
        assertTrue(html.headers().get("Content-Security-Policy").contains("default-src 'none'"));
        assertTrue(html.bodyText().contains("/rooms/{roomId}/variables/bulk-delete"));
        assertTrue(html.bodyText().contains("X-Wired-Read-Key"));
    }

    @Test
    void listsTheVariableNamesByScope() {
        WiredApiResponse response = this.send("GET", BASE + "/variables", READ, null);

        assertEquals(200, response.status());
        assertEquals(
                JsonParser.parseString(
                        "{\"users\":[\"points\",\"vip\"],\"furni\":[\"charge\"],\"global\":[\"score\"]}"),
                json(response));
        assertEquals("60", response.headers().get("X-RateLimit-Limit"));
        assertEquals("59", response.headers().get("X-RateLimit-Remaining"));
        assertNotNull(response.headers().get("X-RateLimit-Reset"));
    }

    @Test
    void listsTheDefinitionsWithTheirSettings() {
        JsonArray variables = json(this.send("GET", BASE + "/variables/definitions", READ, null))
                .getAsJsonArray("variables");

        assertEquals(4, variables.size());
        JsonObject vip = variables.get(1).getAsJsonObject();
        assertEquals("vip", vip.get("name").getAsString());
        assertEquals("user", vip.get("scope").getAsString());
        assertFalse(vip.get("has_value").getAsBoolean());
        assertFalse(vip.get("text_connected").getAsBoolean());
        assertEquals("global", variables.get(3).getAsJsonObject().get("scope").getAsString());
    }

    @Test
    void readsAndWritesOneHolderInHabbosShape() {
        String path = BASE + "/variables/user/points/users/1";
        WiredApiResponse read = this.send("GET", path, READ, null);
        assertEquals(200, read.status());
        assertEquals(
                JsonParser.parseString(
                        "{\"value\":\"30\",\"creation_time\":\"" + TIME + "\",\"update_time\":\"" + TIME + "\"}"),
                json(read));

        WiredApiResponse put = this.send("PUT", BASE + "/variables/user/points/users/2", WRITE, "{\"value\":\"99\"}");
        assertEquals(200, put.status());
        assertEquals("99", json(put).get("value").getAsString());
        assertEquals(TIME, json(put).get("update_time").getAsString());

        assertEquals(
                "-5",
                json(this.send("PATCH", path, WRITE, "{\"value\":\"-5\"}"))
                        .get("value")
                        .getAsString());
        // Older Polaris bodies: a JSON number, and add.
        assertEquals(
                "7",
                json(this.send("PATCH", path, WRITE, "{\"value\":7}"))
                        .get("value")
                        .getAsString());
        assertEquals(
                "4",
                json(this.send("PATCH", path, WRITE, "{\"add\":-3}"))
                        .get("value")
                        .getAsString());

        WiredApiResponse deleted = this.send("DELETE", path, WRITE, null);
        assertEquals(204, deleted.status());
        assertEquals(0, deleted.body().length);
        assertError(this.send("GET", path, READ, null), 404, "wired.variables.not_found");
        assertError(this.send("PATCH", path, WRITE, "{\"value\":\"1\"}"), 404, "wired.variables.not_found");
        assertError(this.send("DELETE", path, WRITE, null), 404, "wired.variables.not_found");
    }

    @Test
    void valueLessVariablesAreCreatedWithoutAValue() {
        String path = BASE + "/variables/user/vip/users/1";

        assertError(this.send("PUT", path, WRITE, "{\"value\":\"1\"}"), 400, "wired.variables.invalid_value");
        WiredApiResponse put = this.send("PUT", path, WRITE, "{}");
        assertEquals(200, put.status());
        assertFalse(json(put).has("value"));
        assertEquals(TIME, json(put).get("creation_time").getAsString());
        assertEquals(200, this.send("PATCH", path, WRITE, "{}").status());
        assertError(this.send("PATCH", path, WRITE, "{\"add\":1}"), 400, "wired.variables.invalid_value");
        assertError(
                this.send("PUT", BASE + "/variables/user/points/users/1", WRITE, "{}"),
                400,
                "wired.variables.invalid_value");
    }

    @Test
    void holdersThatAreNotInTheRoom() {
        assertError(
                this.send("PUT", BASE + "/variables/user/points/users/77", WRITE, "{\"value\":\"1\"}"),
                403,
                "wired.variables.user_not_participating");
        assertError(
                this.send("GET", BASE + "/variables/user/points/users/77", READ, null),
                403,
                "wired.variables.user_not_participating");
        assertError(
                this.send("GET", BASE + "/variables/user/points/pets/77", READ, null),
                404,
                "wired.variables.entity_not_found");
        assertError(
                this.send("GET", BASE + "/variables/furni/charge/furni/77", READ, null),
                404,
                "wired.variables.entity_not_found");
        assertEquals(0, this.room.writes);
    }

    @Test
    void usersWhoAreNotInTheRoomKeepTheirSavedValuesForTheApi() {
        this.room.absentUser(77, "dave");
        this.room.hold("points", 77, 5);
        String path = BASE + "/variables/user/points/users/77";

        assertEquals("5", json(this.send("GET", path, READ, null)).get("value").getAsString());
        assertEquals(
                "9",
                json(this.send("PUT", path, WRITE, "{\"value\":\"9\"}"))
                        .get("value")
                        .getAsString());
        assertEquals(
                "12",
                json(this.send("PATCH", path, WRITE, "{\"add\":3}"))
                        .get("value")
                        .getAsString());

        JsonObject profile = json(this.send("GET", BASE + "/variables_profile/user/users?name=dave", READ, null));
        assertEquals(
                JsonParser.parseString("{\"id\":77,\"name\":\"dave\",\"unique_id\":\"77\"}"),
                profile.getAsJsonObject("user"));
        assertEquals(
                "12",
                profile.getAsJsonObject("variables")
                        .getAsJsonObject("points")
                        .get("value")
                        .getAsString());

        JsonArray items = json(this.send(
                        "GET", BASE + "/variables/user/points/users?order_by=value&order_dir=desc", READ, null))
                .getAsJsonArray("items");
        assertEquals(4, items.size());
        assertEquals(77, items.get(2).getAsJsonObject().get("id").getAsInt());
        assertEquals("dave", items.get(2).getAsJsonObject().get("name").getAsString());
        assertEquals(
                4,
                json(this.send("GET", BASE + "/variables/user/points/users/count", READ, null))
                        .get("count")
                        .getAsInt());

        JsonArray results = json(this.send(
                        "POST",
                        BASE + "/variables/user/points/batch",
                        WRITE,
                        "{\"requests\":[{\"method\":\"PATCH\",\"path\":\"users/77\",\"body\":{\"value\":\"1\"}},"
                                + "{\"method\":\"DELETE\",\"path\":\"users/77\"},"
                                + "{\"method\":\"GET\",\"path\":\"users/78\"}]}"))
                .getAsJsonArray("results");
        assertEquals(200, results.get(0).getAsJsonObject().get("status").getAsInt());
        assertEquals(204, results.get(1).getAsJsonObject().get("status").getAsInt());
        assertEquals(403, results.get(2).getAsJsonObject().get("status").getAsInt());

        assertEquals(
                200,
                this.send(
                                "PATCH",
                                BASE + "/variables_profile/user/users/77",
                                WRITE,
                                "{\"variables\":{\"vip\":true,\"points\":\"4\"}}")
                        .status());
        assertEquals(
                204,
                this.send("DELETE", BASE + "/variables_profile/user/users/77", WRITE, null)
                        .status());
        assertNull(this.room.values.get("points").get(77));
        assertTrue(this.room.unguardedUserCalls.isEmpty(), this.room.unguardedUserCalls::toString);
    }

    @Test
    void addingPastTheIntRangeIsRefused() {
        this.room.hold("points", 1, Integer.MAX_VALUE);

        assertError(
                this.send("PATCH", BASE + "/variables/user/points/users/1", WRITE, "{\"add\":1}"),
                400,
                "wired.variables.invalid_value");
        assertEquals(
                Integer.MAX_VALUE,
                this.room.entry(this.room.variables.get(0), TargetKind.USERS, 1).value());
    }

    @Test
    void pagesHoldersWithHabbosQuery() {
        String path = BASE + "/variables/user/points/users";

        JsonObject page = json(this.send("GET", path + "?order_by=value&order_dir=desc&page=1&size=2", READ, null));
        assertEquals(1, page.get("page").getAsInt());
        assertEquals(2, page.get("size").getAsInt());
        assertFalse(page.has("total"));
        JsonArray items = page.getAsJsonArray("items");
        assertEquals(2, items.size());
        assertEquals(
                JsonParser.parseString("{\"id\":1,\"name\":\"alice\",\"unique_id\":\"1\",\"value\":\"30\","
                        + "\"creation_time\":\"" + TIME + "\",\"update_time\":\"" + TIME + "\"}"),
                items.get(0));
        assertEquals("20", items.get(1).getAsJsonObject().get("value").getAsString());

        JsonObject second = json(this.send("GET", path + "?page=2&size=2", READ, null));
        assertEquals(1, second.getAsJsonArray("items").size());
        assertEquals(
                3,
                second.getAsJsonArray("items")
                        .get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsInt());

        JsonObject defaults = json(this.send("GET", path, READ, null));
        assertEquals(1, defaults.get("page").getAsInt());
        assertEquals(50, defaults.get("size").getAsInt());

        this.room.values.get("points").put(2, new WiredApiRooms.Entry(2, 10, 500, 2_000));
        JsonObject byUpdate = json(this.send("GET", path + "?order_by=update_time&order_dir=desc", READ, null));
        assertEquals(
                2,
                byUpdate.getAsJsonArray("items")
                        .get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsInt());
        JsonObject byCreation = json(this.send("GET", path + "?order_by=creation_time", READ, null));
        assertEquals(
                2,
                byCreation
                        .getAsJsonArray("items")
                        .get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsInt());
    }

    @Test
    void theOlderPolarisQueryNamesStillWork() {
        String path = BASE + "/variables/user/points/users";

        JsonObject page = json(this.send("GET", path + "?page=1&pageSize=2&sort=value&order=desc", READ, null));
        assertEquals(2, page.get("size").getAsInt());
        assertEquals(
                "30",
                page.getAsJsonArray("items")
                        .get(0)
                        .getAsJsonObject()
                        .get("value")
                        .getAsString());
        assertEquals(
                3,
                json(this.send("GET", path + "?sort=entityId&order=desc", READ, null))
                        .getAsJsonArray("items")
                        .get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsInt());
        assertError(this.send("GET", path + "?size=2&pageSize=2", READ, null), 400, "wired.variables.invalid_request");
        assertError(
                this.send("GET", path + "?order_by=value&sort=value", READ, null),
                400,
                "wired.variables.invalid_request");
    }

    @Test
    void pageBoundsAreChecked() {
        String path = BASE + "/variables/user/points/users";

        for (String query : List.of(
                "?page=0",
                "?size=101",
                "?pageSize=101",
                "?size=-1",
                "?page=99999999999",
                "?order_by=name",
                "?order_by=entityId",
                "?sort=name",
                "?order_dir=up",
                "?order=up",
                "?limit=5",
                "?page=1&page=2")) {
            assertError(this.send("GET", path + query, READ, null), 400, "wired.variables.invalid_request");
        }
        assertEquals(200, this.send("GET", path + "?page=1000000", READ, null).status());
    }

    @Test
    void countsHolders() {
        assertEquals(
                JsonParser.parseString("{\"count\":3}"),
                json(this.send("GET", BASE + "/variables/user/points/users/count", READ, null)));
        assertEquals(
                1,
                json(this.send("GET", BASE + "/variables/furni/charge/furni/count", READ, null))
                        .get("count")
                        .getAsInt());
        assertEquals(
                0,
                json(this.send("GET", BASE + "/variables/furni/charge/wall-items/count", READ, null))
                        .get("count")
                        .getAsInt());
    }

    @Test
    void bulkDeleteNeedsTheWriteKeyAndThePermission() {
        String body = "{\"variables\":[\"points\",\"charge\"]}";
        String path = BASE + "/variables/bulk-delete";

        assertError(this.send("POST", path, WRITE, body), 403, "wired.variables.bulk_delete_not_enabled");
        this.room.bulk = true;
        assertError(this.send("POST", path, READ, body), 403, "wired.variables.key_missing");

        WiredApiResponse response = this.send("POST", path, WRITE, body);
        assertEquals(200, response.status());
        assertEquals(3, json(response).getAsJsonObject("deleted").get("points").getAsInt());
        assertEquals(1, json(response).getAsJsonObject("deleted").get("charge").getAsInt());
        // Once a minute per room.
        WiredApiResponse again = this.send("POST", path, WRITE, body);
        assertError(again, 429, "wired.variables.too_many_requests");
        assertNotNull(again.headers().get("Retry-After"));
    }

    @Test
    void bulkDeleteTakesTheOlderNamesBody() {
        this.room.bulk = true;

        WiredApiResponse response =
                this.send("POST", BASE + "/variables/bulk-delete", WRITE, "{\"names\":[\"points\"]}");

        assertEquals(200, response.status());
        assertNull(this.room.values.get("points"));
    }

    @Test
    void bulkDeleteChecksEveryNameFirst() {
        this.room.bulk = true;
        String path = BASE + "/variables/bulk-delete";

        assertError(
                this.send("POST", path, WRITE, "{\"variables\":[\"points\",\"nope\"]}"),
                400,
                "wired.variables.bulk_delete_invalid_variable");
        assertEquals(3, this.room.values.get("points").size());
        assertError(
                this.send("POST", path, WRITE, "{\"variables\":[\"a-b\"]}"),
                400,
                "wired.variables.bulk_delete_invalid_variable");
        assertError(this.send("POST", path, WRITE, "{\"variables\":[]}"), 400, "wired.variables.bulk_delete_empty");
        assertError(this.send("POST", path, WRITE, "{}"), 400, "wired.variables.bulk_delete_empty");
        assertError(
                this.send("POST", path, WRITE, "{\"variables\":[\"points\",\"points\"]}"),
                400,
                "wired.variables.invalid_request");
        assertError(
                this.send("POST", path, WRITE, "{\"variables\":[\"points\"],\"names\":[\"points\"]}"),
                400,
                "wired.variables.invalid_request");
        StringBuilder many = new StringBuilder("{\"variables\":[");
        for (int i = 0; i < 21; i++) {
            many.append(i == 0 ? "" : ",").append("\"v").append(i).append('"');
        }
        assertError(
                this.send("POST", path, WRITE, many.append("]}").toString()),
                400,
                "wired.variables.bulk_delete_limit_exceeded");
        assertEquals(0, this.room.writes);
    }

    @Test
    void batchReportsEachOperationInHabbosShape() {
        String body = "{\"requests\":["
                + "{\"op_id\":\"a\",\"method\":\"PUT\",\"path\":\"users/1\",\"body\":{\"value\":\"4\"}},"
                + "{\"method\":\"PATCH\",\"path\":\"users/2\",\"body\":{\"value\":\"15\"}},"
                + "{\"op_id\":\"c\",\"method\":\"DELETE\",\"path\":\"users/3\"},"
                + "{\"op_id\":\"d\",\"method\":\"GET\",\"path\":\"users/2\"},"
                + "{\"op_id\":\"e\",\"method\":\"PATCH\",\"path\":\"users/77\",\"body\":{\"value\":\"1\"}},"
                + "{\"op_id\":\"f\",\"method\":\"GET\",\"path\":\"users/3\"}]}";

        WiredApiResponse response = this.both("POST", BASE + "/variables/user/points/batch", body);

        assertEquals(200, response.status());
        JsonArray results = json(response).getAsJsonArray("results");
        assertEquals(6, results.size());
        JsonObject put = results.get(0).getAsJsonObject();
        assertEquals("a", put.get("op_id").getAsString());
        assertEquals(200, put.get("status").getAsInt());
        assertEquals("4", put.getAsJsonObject("body").get("value").getAsString());
        assertTrue(results.get(1).getAsJsonObject().get("op_id").isJsonNull());
        assertEquals(
                "15",
                results.get(1)
                        .getAsJsonObject()
                        .getAsJsonObject("body")
                        .get("value")
                        .getAsString());
        assertEquals(JsonParser.parseString("{\"op_id\":\"c\",\"status\":204}"), results.get(2));
        assertEquals(
                "15",
                results.get(3)
                        .getAsJsonObject()
                        .getAsJsonObject("body")
                        .get("value")
                        .getAsString());
        assertEquals(
                JsonParser.parseString("{\"op_id\":\"e\",\"status\":403,\"error\":{"
                        + "\"code\":\"wired.variables.user_not_participating\","
                        + "\"message\":\"wired.variables.user_not_participating\"}}"),
                results.get(4));
        assertEquals(404, results.get(5).getAsJsonObject().get("status").getAsInt());
        assertEquals(
                "wired.variables.not_found",
                results.get(5)
                        .getAsJsonObject()
                        .getAsJsonObject("error")
                        .get("code")
                        .getAsString());
    }

    @Test
    void aBatchOfReadsNeedsOnlyTheReadKey() {
        String reads = "{\"requests\":[{\"method\":\"GET\",\"path\":\"users/1\"}]}";
        String writes = "{\"requests\":[{\"method\":\"GET\",\"path\":\"users/1\"},"
                + "{\"method\":\"PUT\",\"path\":\"users/1\",\"body\":{\"value\":\"1\"}}]}";
        String path = BASE + "/variables/user/points/batch";

        assertEquals(200, this.send("POST", path, READ, reads).status());
        assertError(this.send("POST", path, READ, writes), 403, "wired.variables.key_missing");
        assertEquals(0, this.room.writes);
        assertEquals(200, this.send("POST", path, WRITE, writes).status());
    }

    @Test
    void theOlderBatchBodyStillWorks() {
        String body = "{\"operations\":["
                + "{\"op\":\"set\",\"targetKind\":\"users\",\"entityId\":1,\"value\":4},"
                + "{\"op\":\"add\",\"targetKind\":\"users\",\"entityId\":2,\"value\":5},"
                + "{\"op\":\"delete\",\"targetKind\":\"users\",\"entityId\":3},"
                + "{\"op\":\"add\",\"targetKind\":\"users\",\"entityId\":77,\"value\":1}]}";

        WiredApiResponse response = this.legacy("POST", BASE + "/variables/user/points/batch", WRITE, body);

        assertEquals(200, response.status());
        JsonArray results = json(response).getAsJsonArray("results");
        assertEquals(
                "4",
                results.get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("body")
                        .get("value")
                        .getAsString());
        assertEquals(
                "15",
                results.get(1)
                        .getAsJsonObject()
                        .getAsJsonObject("body")
                        .get("value")
                        .getAsString());
        assertEquals(204, results.get(2).getAsJsonObject().get("status").getAsInt());
        assertEquals(403, results.get(3).getAsJsonObject().get("status").getAsInt());
    }

    @Test
    void batchShapeIsCheckedBeforeAnythingIsWritten() {
        String path = BASE + "/variables/user/points/batch";
        String ok = "{\"method\":\"PUT\",\"path\":\"users/1\",\"body\":{\"value\":\"1\"}}";

        assertError(
                this.both("POST", path, "{\"requests\":[" + ok + ",{\"method\":\"POST\",\"path\":\"users/1\"}]}"),
                400,
                "wired.variables.invalid_request");
        for (String target : List.of("furni/1", "users/0", "users", "users/1/2", "cats/1", "/users/1")) {
            assertError(
                    this.both(
                            "POST",
                            path,
                            "{\"requests\":[" + ok + ",{\"method\":\"GET\",\"path\":\"" + target + "\"}]}"),
                    400,
                    "wired.variables.invalid_target");
        }
        assertError(
                this.both(
                        "POST",
                        path,
                        "{\"requests\":[" + ok
                                + ",{\"method\":\"PUT\",\"path\":\"users/1\",\"body\":{\"value\":\"x\"}}]}"),
                400,
                "wired.variables.invalid_value");
        assertError(
                this.both(
                        "POST", path, "{\"requests\":[" + ok + ",{\"method\":\"GET\",\"path\":\"users/1\",\"x\":1}]}"),
                400,
                "wired.variables.invalid_request");
        assertError(
                this.legacy(
                        "POST",
                        path,
                        WRITE,
                        "{\"operations\":[{\"op\":\"set\",\"targetKind\":\"floor\",\"entityId\":1,\"value\":1}]}"),
                400,
                "wired.variables.invalid_target");
        assertError(this.both("POST", path, "{\"requests\":[]}"), 400, "wired.variables.batch_empty");
        StringBuilder many = new StringBuilder("{\"requests\":[");
        for (int i = 0; i < 101; i++) {
            many.append(i == 0 ? "" : ",").append("{\"method\":\"GET\",\"path\":\"users/1\"}");
        }
        assertError(this.both("POST", path, many.append("]}").toString()), 400, "wired.variables.batch_limit_exceeded");
        assertEquals(0, this.room.writes);
    }

    @Test
    void readsAndChangesGlobalVariables() {
        String path = BASE + "/variables/global/score";

        JsonObject read = json(this.send("GET", path, READ, null));
        assertEquals("0", read.get("value").getAsString());
        assertEquals("1970-01-01T00:00:00Z", read.get("creation_time").getAsString());
        assertEquals(
                "12",
                json(this.send("PATCH", path, WRITE, "{\"value\":\"12\"}"))
                        .get("value")
                        .getAsString());
        assertEquals(
                "15",
                json(this.send("PATCH", path, WRITE, "{\"add\":3}"))
                        .get("value")
                        .getAsString());
        assertError(this.send("PATCH", path, WRITE, "{\"value\":1,\"add\":1}"), 400, "wired.variables.invalid_value");
        assertError(this.send("PATCH", path, WRITE, "{}"), 400, "wired.variables.invalid_value");
        assertError(this.send("GET", BASE + "/variables/global/points", READ, null), 404, "wired.variables.not_found");
    }

    @Test
    void userProfilesInHabbosShape() {
        JsonObject byName = json(this.send("GET", BASE + "/variables_profile/user/users?name=alice", READ, null));
        assertEquals(
                JsonParser.parseString("{\"user\":{\"id\":1,\"name\":\"alice\",\"unique_id\":\"1\"},\"variables\":{"
                        + "\"points\":{\"value\":\"30\",\"creation_time\":\"" + TIME + "\",\"update_time\":\""
                        + TIME + "\"}}}"),
                byName);

        JsonObject byId = json(this.send("GET", BASE + "/variables_profile/user/users?unique_id=2", READ, null));
        assertEquals("bob", byId.getAsJsonObject("user").get("name").getAsString());

        assertError(
                this.send("GET", BASE + "/variables_profile/user/users", READ, null),
                400,
                "wired.variables.invalid_request");
        assertError(
                this.send("GET", BASE + "/variables_profile/user/users?name=a&unique_id=1", READ, null),
                400,
                "wired.variables.invalid_request");
        assertError(
                this.send("GET", BASE + "/variables_profile/user/users?name=zed", READ, null),
                404,
                "wired.variables.entity_not_found");
        assertError(
                this.send("GET", BASE + "/variables_profile/user/users?unique_id=hhes-617d5a", READ, null),
                404,
                "wired.variables.entity_not_found");
        assertError(
                this.send("GET", BASE + "/variables_profile/user/users?unique_id=77", READ, null),
                403,
                "wired.variables.user_not_participating");
        assertEquals(
                3,
                json(this.send("GET", BASE + "/variables_profile/user/users/3", READ, null))
                        .getAsJsonObject("user")
                        .get("id")
                        .getAsInt());
    }

    @Test
    void patchesAndDeletesAUserProfile() {
        String path = BASE + "/variables_profile/user/users/1";

        JsonObject patched = json(this.send("PATCH", path, WRITE, "{\"variables\":{\"points\":null,\"vip\":true}}"));
        assertFalse(patched.getAsJsonObject("variables").has("points"));
        assertTrue(patched.getAsJsonObject("variables").has("vip"));
        assertEquals(
                "8",
                json(this.send("PATCH", path, WRITE, "{\"variables\":{\"points\":\"8\"}}"))
                        .getAsJsonObject("variables")
                        .getAsJsonObject("points")
                        .get("value")
                        .getAsString());

        assertError(
                this.send("PATCH", path, WRITE, "{\"variables\":{\"vip\":\"3\"}}"),
                400,
                "wired.variables.invalid_value");
        assertError(
                this.send("PATCH", path, WRITE, "{\"variables\":{\"points\":true}}"),
                400,
                "wired.variables.invalid_value");
        assertError(
                this.send("PATCH", path, WRITE, "{\"variables\":{\"points\":\"x\"}}"),
                400,
                "wired.variables.invalid_value");
        assertError(
                this.send("PATCH", path, WRITE, "{\"variables\":{\"charge\":1}}"), 404, "wired.variables.not_found");

        WiredApiResponse deleted = this.send("DELETE", path, WRITE, null);
        assertEquals(204, deleted.status());
        assertEquals(
                0,
                json(this.send("GET", path, READ, null))
                        .getAsJsonObject("variables")
                        .size());
    }

    @Test
    void profilesNameTheirOwnerByKind() {
        this.room.pet(4, "DragonDog").bot(6, "Frank");

        assertEquals(
                JsonParser.parseString("{\"id\":4,\"name\":\"DragonDog\"}"),
                json(this.send("GET", BASE + "/variables_profile/user/pets/4", READ, null))
                        .get("pet"));
        assertEquals(
                "Frank",
                json(this.send("GET", BASE + "/variables_profile/user/bots/6", READ, null))
                        .getAsJsonObject("bot")
                        .get("name")
                        .getAsString());
        JsonObject furni = json(this.send("GET", BASE + "/variables_profile/furni/furni/40", READ, null));
        assertEquals(JsonParser.parseString("{\"id\":40}"), furni.get("furni"));
        assertEquals(
                "7",
                furni.getAsJsonObject("variables")
                        .getAsJsonObject("charge")
                        .get("value")
                        .getAsString());
        assertTrue(json(this.send("GET", BASE + "/variables_profile/furni/wall-items/41", READ, null))
                .has("wall_item"));
        // The older kind names still work and answer with Habbo's owner field.
        assertTrue(json(this.send("GET", BASE + "/variables_profile/furni/floor/40", READ, null))
                .has("furni"));
        assertTrue(json(this.send("GET", BASE + "/variables_profile/furni/wall/41", READ, null))
                .has("wall_item"));
        assertError(
                this.send("GET", BASE + "/variables_profile/furni/wall-items/40", READ, null),
                404,
                "wired.variables.entity_not_found");

        JsonObject global = json(this.send("GET", BASE + "/variables_profile/global", READ, null));
        assertEquals(1, global.size());
        assertEquals(
                "0",
                global.getAsJsonObject("variables")
                        .getAsJsonObject("score")
                        .get("value")
                        .getAsString());
        JsonObject patched = json(
                this.send("PATCH", BASE + "/variables_profile/global", WRITE, "{\"variables\":{\"score\":\"42\"}}"));
        assertEquals(
                "42",
                patched.getAsJsonObject("variables")
                        .getAsJsonObject("score")
                        .get("value")
                        .getAsString());
        assertError(
                this.send("PATCH", BASE + "/variables_profile/global", WRITE, "{\"variables\":{\"score\":null}}"),
                400,
                "wired.variables.invalid_value");
    }

    @Test
    void buildersClubItemsHaveTheirOwnKinds() {
        this.room.item(50, TargetKind.FURNI_BC).item(51, TargetKind.WALL_ITEMS_BC);
        this.room.hold("charge", 50, 3);

        JsonObject profile = json(this.send("GET", BASE + "/variables_profile/furni/furni-bc/50", READ, null));
        assertEquals(50, profile.getAsJsonObject("furni_bc").get("id").getAsInt());
        assertTrue(json(this.send("GET", BASE + "/variables_profile/furni/wall-items-bc/51", READ, null))
                .has("wall_item_bc"));
        assertEquals(
                "3",
                json(this.send("GET", BASE + "/variables/furni/charge/furni-bc/50", READ, null))
                        .get("value")
                        .getAsString());
        assertError(
                this.send("GET", BASE + "/variables/furni/charge/furni/50", READ, null),
                404,
                "wired.variables.entity_not_found");
        assertEquals(
                1,
                json(this.send("GET", BASE + "/variables/furni/charge/furni-bc/count", READ, null))
                        .get("count")
                        .getAsInt());
    }

    @Test
    void habbosKeyHeaders() {
        String read = BASE + "/variables";
        String write = BASE + "/variables/user/points/users/1";

        assertEquals(
                200,
                this.request("GET", read, Map.of("x-wired-read-key", READ), null)
                        .status());
        // The write key opens reads too, in either header.
        assertEquals(
                200,
                this.request("GET", read, Map.of("x-wired-read-key", WRITE), null)
                        .status());
        assertEquals(
                200,
                this.request("GET", read, Map.of("x-wired-write-key", WRITE), null)
                        .status());
        assertEquals(
                200,
                this.request(
                                "PUT",
                                write,
                                Map.of("x-wired-write-key", WRITE, "content-type", "application/json"),
                                "{\"value\":\"2\"}")
                        .status());
        assertError(
                this.request(
                        "PUT",
                        write,
                        Map.of("x-wired-read-key", READ, "content-type", "application/json"),
                        "{\"value\":\"2\"}"),
                403,
                "wired.variables.key_missing");
        // The write header takes only the write key.
        assertError(
                this.request("GET", read, Map.of("x-wired-write-key", READ), null), 403, "wired.variables.key_invalid");
        // Every key sent must be valid.
        assertError(
                this.request(
                        "GET",
                        read,
                        Map.of("x-wired-read-key", READ, "x-wired-write-key", WiredExtraVariableWebApi.mintKey()),
                        null),
                403,
                "wired.variables.key_invalid");
        assertEquals(
                200,
                this.request("GET", read, Map.of("x-wired-read-key", READ, "x-wired-write-key", WRITE), null)
                        .status());
    }

    @Test
    void theOlderKeyHeadersStillWork() {
        String read = BASE + "/variables";

        assertEquals(200, this.legacy("GET", read, READ, null).status());
        assertEquals(
                200, this.request("GET", read, Map.of("x-api-key", READ), null).status());
        assertEquals(
                200,
                this.legacy("PUT", BASE + "/variables/user/points/users/1", WRITE, "{\"value\":1}")
                        .status());
        assertError(
                this.legacy("PUT", BASE + "/variables/user/points/users/1", READ, "{\"value\":1}"),
                403,
                "wired.variables.key_missing");
    }

    @Test
    void authenticationFailures() {
        String path = BASE + "/variables";

        WiredApiResponse missing = this.send("GET", path, null, null);
        assertError(missing, 403, "wired.variables.key_missing");
        assertNull(missing.headers().get("WWW-Authenticate"));
        assertError(
                this.send("GET", path, WiredExtraVariableWebApi.mintKey(), null), 403, "wired.variables.key_invalid");
        assertError(this.send("GET", path, "short", null), 403, "wired.variables.key_invalid");
        assertError(
                this.request("GET", path, Map.of("authorization", "Basic " + READ), null),
                403,
                "wired.variables.key_missing");
        for (String name : List.of("key", "access_token", "X-Wired-Read-Key", "write_key")) {
            assertError(
                    this.send("GET", path + "?" + name + "=" + READ, READ, null),
                    400,
                    "wired.variables.invalid_request");
        }
    }

    @Test
    void aKeyOnlyOpensItsOwnRoom() {
        String otherRead = WiredExtraVariableWebApi.mintKey();
        this.rooms.room(6, otherRead, WiredExtraVariableWebApi.mintKey());

        // The same answer for another room's key and a room that does not exist.
        assertError(this.send("GET", "/api/public/rooms/6/variables", READ, null), 403, "wired.variables.key_invalid");
        assertError(this.send("GET", BASE + "/variables", otherRead, null), 403, "wired.variables.key_invalid");
        assertError(
                this.send("GET", "/api/public/rooms/404/variables", READ, null), 403, "wired.variables.key_invalid");
    }

    @Test
    void aBoxOutsideItsOwnersRoomIsInert() {
        this.room.usable = false;

        assertError(this.send("GET", BASE + "/variables", READ, null), 403, "wired.variables.api_disabled");
    }

    @Test
    void anUnloadedRoomIsLoadedOnlyForAMatchingKey() {
        FakeRoom unloaded = new FakeRoom(8, READ, WRITE);
        unloaded.variable("points", Scope.USER, true);
        this.rooms.unloaded.put(8, unloaded);

        assertError(
                this.send("GET", "/api/public/rooms/8/variables", WiredExtraVariableWebApi.mintKey(), null),
                403,
                "wired.variables.key_invalid");
        assertError(
                this.request("GET", "/api/public/rooms/8/variables", Map.of("x-wired-write-key", READ), null),
                403,
                "wired.variables.key_invalid");
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
        for (String room : List.of("0", "abc", "2147483648", "-5", "05", "r-hhes-1")) {
            assertError(
                    this.send("GET", "/api/public/rooms/" + room + "/variables", READ, null), 404, "room.not_found");
        }
        assertError(
                this.send("GET", BASE + "/variables/user/bad-name/users/1", READ, null),
                404,
                "wired.variables.not_found");
        assertError(
                this.send("GET", BASE + "/variables/user/" + "a".repeat(41) + "/users/1", READ, null),
                404,
                "wired.variables.not_found");
        assertError(
                this.send("GET", BASE + "/variables/global/score/users/1", READ, null),
                400,
                "wired.variables.invalid_target");
        assertError(
                this.send("GET", BASE + "/variables/room/score/users/1", READ, null),
                400,
                "wired.variables.invalid_target");
        assertError(
                this.send("GET", BASE + "/variables/user/points/furni/1", READ, null),
                400,
                "wired.variables.invalid_target");
        assertError(
                this.send("GET", BASE + "/variables/furni/charge/users/1", READ, null),
                400,
                "wired.variables.invalid_target");
        assertError(
                this.send("GET", BASE + "/variables/user/points/users/0", READ, null),
                400,
                "wired.variables.invalid_target");
        assertError(
                this.send("GET", BASE + "/variables/user/points/users/9999999999", READ, null),
                400,
                "wired.variables.invalid_target");
        assertError(
                this.send("GET", BASE + "/variables/user/nope/users/1", READ, null), 404, "wired.variables.not_found");
        assertError(
                this.send("GET", BASE + "/variables/furni/points/furni/40", READ, null),
                404,
                "wired.variables.not_found");
        assertError(this.send("GET", BASE + "/nothing", READ, null), 404, "wired.variables.unknown_endpoint");
        assertError(this.send("GET", BASE + "/variables/", READ, null), 404, "wired.variables.unknown_endpoint");
        assertError(
                this.send("GET", "/api/public/rooms//variables", READ, null), 404, "wired.variables.unknown_endpoint");

        WiredApiResponse wrongMethod = this.send("POST", BASE + "/variables", WRITE, "{}");
        assertError(wrongMethod, 405, "wired.variables.method_not_allowed");
        assertEquals("GET", wrongMethod.headers().get("Allow"));
    }

    @Test
    void petsAndBotsHoldUserVariablesApartFromUsersWithTheSameId() {
        this.room.pet(1, "DragonDog").bot(1, "Frank");

        assertError(
                this.send("GET", BASE + "/variables/user/points/pets/1", READ, null), 404, "wired.variables.not_found");
        assertEquals(
                200,
                this.send("PUT", BASE + "/variables/user/points/pets/1", WRITE, "{\"value\":\"7\"}")
                        .status());
        assertEquals(
                200,
                this.send("PUT", BASE + "/variables/user/points/bots/1", WRITE, "{\"value\":\"9\"}")
                        .status());

        assertEquals(
                "7",
                json(this.send("GET", BASE + "/variables/user/points/pets/1", READ, null))
                        .get("value")
                        .getAsString());
        assertEquals(
                "9",
                json(this.send("GET", BASE + "/variables/user/points/bots/1", READ, null))
                        .get("value")
                        .getAsString());
        JsonArray pets = json(this.send("GET", BASE + "/variables/user/points/pets", READ, null))
                .getAsJsonArray("items");
        assertEquals(1, pets.size());
        assertEquals("DragonDog", pets.get(0).getAsJsonObject().get("name").getAsString());
        assertFalse(pets.get(0).getAsJsonObject().has("unique_id"));
        assertEquals(
                "30",
                json(this.send("GET", BASE + "/variables/user/points/users/1", READ, null))
                        .get("value")
                        .getAsString());
        assertError(
                this.send("GET", BASE + "/variables/user/points/cats/1", READ, null),
                400,
                "wired.variables.invalid_target");
    }

    @Test
    void bodiesAreStrict() {
        String path = BASE + "/variables/user/points/users/1";

        for (String value : List.of(
                "1.5",
                "1e3",
                "2147483648",
                "\"2147483648\"",
                "\"9223372036854775807\"",
                "\"x\"",
                "\"1.0\"",
                "\" 1\"",
                "true",
                "null",
                "[1]")) {
            assertError(
                    this.send("PUT", path, WRITE, "{\"value\":" + value + "}"), 400, "wired.variables.invalid_value");
        }
        for (String body : List.of(
                "{\"value\":1,\"value\":2}",
                "{\"value\":1,\"other\":2}",
                "{\"value\":1} x",
                "{value:1}",
                "[1]",
                "",
                "{\"value\":1")) {
            assertError(this.send("PUT", path, WRITE, body), 400, "wired.variables.invalid_request");
        }
        assertError(this.send("GET", path, READ, "{}"), 400, "wired.variables.invalid_request");
        assertError(
                this.request(
                        "PUT",
                        path,
                        Map.of("x-wired-write-key", WRITE, "content-type", "text/plain"),
                        "{\"value\":\"1\"}"),
                400,
                "wired.variables.invalid_request");
        assertEquals(
                200,
                this.send("PUT", path, WRITE, "{\"value\":\"-2147483648\"}").status());
        assertEquals(
                200, this.send("PUT", path, WRITE, "{\"value\":-2147483648}").status());

        assertError(
                this.send("PUT", path, WRITE, "{\"value\":1" + " ".repeat(17_000) + "}"),
                413,
                "wired.variables.payload_too_large");
    }

    @Test
    void perAddressLimitAnswersTooManyRequests() {
        this.settings.set(with(3, 60, 200, 10));

        for (int i = 0; i < 3; i++) {
            assertEquals(
                    200, this.send("GET", "/api/public/api-docs", null, null).status());
        }
        WiredApiResponse limited = this.send("GET", "/api/public/api-docs", null, null);
        assertError(limited, 429, "wired.variables.too_many_requests");
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
        assertEquals(200, this.legacy("GET", BASE + "/variables", READ, null).status());
        assertError(this.send("GET", BASE + "/variables", READ, null), 429, "wired.variables.too_many_requests");
        assertEquals(200, this.send("GET", BASE + "/variables", WRITE, null).status());
    }

    @Test
    void perRoomWriteLimitCountsEveryWrite() {
        this.settings.set(with(1000, 1000, 3, 10));
        String batch = "{\"requests\":["
                + "{\"method\":\"PUT\",\"path\":\"users/1\",\"body\":{\"value\":\"1\"}},"
                + "{\"method\":\"GET\",\"path\":\"users/1\"},"
                + "{\"method\":\"PUT\",\"path\":\"users/2\",\"body\":{\"value\":\"1\"}}]}";

        assertEquals(
                200,
                this.both("POST", BASE + "/variables/user/points/batch", batch).status());
        assertEquals(
                200,
                this.send("PUT", BASE + "/variables/user/points/users/1", WRITE, "{\"value\":\"2\"}")
                        .status());
        assertError(
                this.send("PUT", BASE + "/variables/user/points/users/1", WRITE, "{\"value\":\"3\"}"),
                429,
                "wired.variables.too_many_requests");
        assertEquals(2, this.room.values.get("points").get(1).value());
        assertEquals(
                200,
                this.send("GET", BASE + "/variables/user/points/users/1", READ, null)
                        .status());
    }

    @Test
    void repeatedFailedAuthenticationBlocksTheAddress() {
        this.settings.set(with(1000, 1000, 200, 2));

        assertError(
                this.send("GET", BASE + "/variables", WiredExtraVariableWebApi.mintKey(), null),
                403,
                "wired.variables.key_invalid");
        assertError(this.send("GET", BASE + "/variables", null, null), 403, "wired.variables.key_missing");
        assertError(
                this.send("GET", BASE + "/variables", WiredExtraVariableWebApi.mintKey(), null),
                403,
                "wired.variables.key_invalid");
        WiredApiResponse blocked = this.send("GET", BASE + "/variables", READ, null);
        assertError(blocked, 429, "wired.variables.too_many_requests");
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
        String allowed = preflight.headers().get("Access-Control-Allow-Headers");
        for (String header :
                List.of("X-Wired-Read-Key", "X-Wired-Write-Key", "Authorization", "X-Api-Key", "Content-Type")) {
            assertTrue(allowed.contains(header), header);
        }
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
        WiredApiResponse ok = this.request(
                "GET", BASE + "/variables", Map.of("origin", "https://good.test", "x-wired-read-key", READ), null);
        assertEquals("https://good.test", ok.headers().get("Access-Control-Allow-Origin"));
        assertEquals("Origin", ok.headers().get("Vary"));
        assertTrue(ok.headers().get("Access-Control-Expose-Headers").contains("Retry-After"));
    }

    @Test
    void errorsNeverEchoTheKey() {
        WiredApiResponse response = this.send("GET", BASE + "/variables?key=" + READ, null, null);

        assertFalse(response.bodyText().contains(READ));
        assertEquals(1, json(response).size());
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

    /** Like the habbo-sdk: the write key in X-Wired-Write-Key, any other key in X-Wired-Read-Key. */
    private WiredApiResponse send(String method, String uri, String key, String body) {
        Map<String, String> headers = new HashMap<>();
        if (key != null) {
            headers.put(key.equals(WRITE) ? "x-wired-write-key" : "x-wired-read-key", key);
        }
        return this.request(method, uri, withBodyType(headers, method, body), body);
    }

    /** A batch as the habbo-sdk sends it: both keys. */
    private WiredApiResponse both(String method, String uri, String body) {
        Map<String, String> headers = new HashMap<>(Map.of("x-wired-read-key", READ, "x-wired-write-key", WRITE));
        return this.request(method, uri, withBodyType(headers, method, body), body);
    }

    /** The older Polaris form: either key as a bearer token. */
    private WiredApiResponse legacy(String method, String uri, String key, String body) {
        Map<String, String> headers = new HashMap<>(Map.of("authorization", "Bearer " + key));
        return this.request(method, uri, withBodyType(headers, method, body), body);
    }

    private static Map<String, String> withBodyType(Map<String, String> headers, String method, String body) {
        if (body != null && !method.equals("GET")) {
            headers.put("content-type", "application/json");
        }
        return headers;
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

    /** Habbo's error: the status and a body that is exactly {"error":"<code>"}. */
    private static void assertError(WiredApiResponse response, int status, String code) {
        assertEquals(status, response.status(), response.bodyText());
        JsonObject expected = new JsonObject();
        expected.addProperty("error", code);
        assertEquals(expected, json(response));
    }
}
