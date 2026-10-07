package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore.Order;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Entry;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Scope;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.TargetKind;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Variable;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.VariableRoom;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The operations behind each route, answering in Habbo's shapes. Paths and keys are already
 * validated by the router. Older Polaris bodies and query names are still accepted. The calls for
 * one holder run as one step ({@link VariableRoom#atomically}), so a user entering the room midway
 * does not split them between the saved rows and the live store.
 */
final class WiredApiEndpoints {
    private static final Pattern PAGE = Pattern.compile("[1-9][0-9]{0,6}");
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_OP_ID = 64;
    private static final Set<String> METHODS = Set.of("GET", "PUT", "PATCH", "DELETE");

    /** One routed call: the request, its validated path values and the authenticated session. */
    record Call(
            WiredApiRequest request,
            Map<String, String> params,
            WiredApiAuth.Session session,
            WiredApiSettings settings,
            WiredApiLimits limits) {

        VariableRoom room() {
            return this.session.room();
        }

        int intParam(String name) {
            return Integer.parseInt(this.params.get(name));
        }

        TargetKind kind() {
            return TargetKind.fromPath(this.params.get("targetKind"));
        }

        Scope scope() {
            return Scope.fromPath(this.params.get("scope"));
        }

        void writes(int count) {
            this.limits.acquireRoomWrites(this.room().id(), count, this.settings);
        }

        void bulkDelete() {
            this.limits.acquireBulkDelete(this.room().id());
        }
    }

    private WiredApiEndpoints() {}

    static WiredApiResponse listVariables(Call call) {
        JsonArray users = new JsonArray();
        JsonArray furni = new JsonArray();
        JsonArray global = new JsonArray();
        for (Variable variable : call.room().variables()) {
            switch (variable.scope()) {
                case USER -> users.add(variable.name());
                case FURNI -> furni.add(variable.name());
                case GLOBAL -> global.add(variable.name());
            }
        }
        JsonObject body = new JsonObject();
        body.add("users", users);
        body.add("furni", furni);
        body.add("global", global);
        return ok(body);
    }

    static WiredApiResponse listDefinitions(Call call) {
        JsonArray variables = new JsonArray();
        for (Variable variable : call.room().variables()) {
            variables.add(WiredApiJson.definition(variable));
        }
        JsonObject body = new JsonObject();
        body.add("variables", variables);
        return ok(body);
    }

    static WiredApiResponse getEntry(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        return call.room().atomically(kind, entityId, () -> {
            requireHolder(call.room(), kind, entityId);
            Entry entry = call.room().entry(variable, kind, entityId);
            if (entry == null) {
                throw notHeld();
            }
            return ok(WiredApiJson.stored(entry));
        });
    }

    static WiredApiResponse putEntry(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        Map<String, Object> body = WiredApiJson.parseObject(call.request().body());
        WiredApiJson.allowOnly(body, Set.of("value"));
        Integer value = body.containsKey("value") ? WiredApiJson.value(body.get("value"), "value") : null;
        checkValueShape(variable, value != null);

        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        return call.room().atomically(kind, entityId, () -> {
            requireHolder(call.room(), kind, entityId);
            call.writes(1);
            return ok(WiredApiJson.stored(assign(call.room(), variable, kind, entityId, value)));
        });
    }

    static WiredApiResponse patchEntry(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        Change change = change(WiredApiJson.parseObject(call.request().body()), variable, true);
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        return call.room().atomically(kind, entityId, () -> {
            requireHolder(call.room(), kind, entityId);
            if (call.room().entry(variable, kind, entityId) == null) {
                throw notHeld();
            }
            if (change != null) {
                call.writes(1);
            }
            return ok(WiredApiJson.stored(update(call.room(), variable, kind, entityId, change)));
        });
    }

    static WiredApiResponse deleteEntry(Call call) {
        noBody(call);
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        return call.room().atomically(kind, entityId, () -> {
            requireHolder(call.room(), kind, entityId);
            if (call.room().entry(variable, kind, entityId) == null) {
                throw notHeld();
            }
            call.writes(1);
            call.room().remove(variable, kind, entityId);
            return WiredApiResponse.empty(204);
        });
    }

    static WiredApiResponse listEntries(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        WiredApiRequest request = call.request();
        int page = pageParam(request.queryValue("page"), 1, 1_000_000, "page");
        int size = pageParam(
                either(request, "size", "pageSize"),
                Math.min(DEFAULT_PAGE_SIZE, call.settings().maxPageSize()),
                call.settings().maxPageSize(),
                "size");
        String orderBy = orderBy(request);
        String orderDir = either(request, "order_dir", "order");
        if (orderDir == null) {
            orderDir = "asc";
        } else if (!orderDir.equals("asc") && !orderDir.equals("desc")) {
            throw WiredApiException.badRequest("'order_dir' must be asc or desc.");
        }

        TargetKind kind = call.kind();
        Order order =
                switch (orderBy) {
                    case "value" -> Order.VALUE;
                    case "creation_time" -> Order.CREATION_TIME;
                    case "update_time" -> Order.UPDATE_TIME;
                    default -> Order.ID;
                };
        int offset = (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
        List<Entry> entries = call.room().holderPage(variable, kind, order, orderDir.equals("desc"), offset, size);

        JsonArray items = new JsonArray();
        for (Entry entry : entries) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.entityId());
            if (kind.scope() == Scope.USER) {
                String name = entry.name() != null ? entry.name() : call.room().holderName(kind, entry.entityId());
                if (name != null) {
                    item.addProperty("name", name);
                }
                if (kind == TargetKind.USERS) {
                    item.addProperty("unique_id", Integer.toString(entry.entityId()));
                }
            }
            WiredApiJson.stored(entry).entrySet().forEach(field -> item.add(field.getKey(), field.getValue()));
            items.add(item);
        }
        JsonObject body = new JsonObject();
        body.add("items", items);
        body.addProperty("page", page);
        body.addProperty("size", size);
        return ok(body);
    }

    /** Habbo's {@code order_by}, or the older {@code sort}; null means the holder id. */
    private static String orderBy(WiredApiRequest request) {
        String orderBy = request.queryValue("order_by");
        String sort = request.queryValue("sort");
        if (orderBy != null && sort != null) {
            throw WiredApiException.badRequest("Give 'order_by' or 'sort', not both.");
        }
        if (orderBy != null) {
            if (!Set.of("value", "creation_time", "update_time").contains(orderBy)) {
                throw WiredApiException.badRequest("'order_by' must be value, creation_time or update_time.");
            }
            return orderBy;
        }
        if (sort != null && !sort.equals("value") && !sort.equals("entityId")) {
            throw WiredApiException.badRequest("'sort' must be value or entityId.");
        }
        return sort == null || sort.equals("entityId") ? "id" : sort;
    }

    static WiredApiResponse countEntries(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        JsonObject body = new JsonObject();
        body.addProperty("count", call.room().holderCount(variable, call.kind()));
        return ok(body);
    }

    static WiredApiResponse bulkDelete(Call call) {
        Map<String, Object> body = WiredApiJson.parseObject(call.request().body());
        WiredApiJson.allowOnly(body, Set.of("variables", "names"));
        if (body.containsKey("variables") && body.containsKey("names")) {
            throw WiredApiException.badRequest("Give 'variables' or 'names', not both.");
        }
        String field = body.containsKey("names") ? "names" : "variables";
        if (!body.containsKey(field)) {
            throw new WiredApiException(400, WiredApiException.BULK_DELETE_EMPTY, "'variables' is required.");
        }
        List<Object> names = WiredApiJson.arrayValue(body.get(field), field);
        if (names.isEmpty()) {
            throw new WiredApiException(400, WiredApiException.BULK_DELETE_EMPTY, "No variables given.");
        }
        if (names.size() > call.settings().maxBulkNames()) {
            throw new WiredApiException(
                    400,
                    WiredApiException.BULK_DELETE_LIMIT_EXCEEDED,
                    "At most " + call.settings().maxBulkNames() + " variables.");
        }
        List<Variable> variables = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object raw : names) {
            String name = raw instanceof String text ? text : "";
            if (!HotelWiredApiRooms.NAME.matcher(name).matches() || findVariable(call.room(), null, name) == null) {
                throw new WiredApiException(
                        400, WiredApiException.BULK_DELETE_INVALID_VARIABLE, "Not a variable of this room.");
            }
            if (!seen.add(name)) {
                throw WiredApiException.badRequest("Duplicate variable name '" + name + "'.");
            }
            variables.add(findVariable(call.room(), null, name));
        }
        call.writes(variables.size());
        call.bulkDelete();
        JsonObject deleted = new JsonObject();
        for (Variable variable : variables) {
            deleted.addProperty(variable.name(), call.room().clearAll(variable));
        }
        JsonObject response = new JsonObject();
        response.add("deleted", deleted);
        return ok(response);
    }

    static WiredApiResponse batch(Call call) {
        Scope scope = call.scope();
        Variable variable = variable(call.room(), scope, call.params().get("variableName"));
        Map<String, Object> body = WiredApiJson.parseObject(call.request().body());
        WiredApiJson.allowOnly(body, Set.of("requests", "operations"));
        if (body.containsKey("requests") && body.containsKey("operations")) {
            throw WiredApiException.badRequest("Give 'requests' or 'operations', not both.");
        }
        boolean legacy = body.containsKey("operations");
        String field = legacy ? "operations" : "requests";
        if (!body.containsKey(field)) {
            throw new WiredApiException(400, WiredApiException.BATCH_EMPTY, "'requests' is required.");
        }
        List<Object> raw = WiredApiJson.arrayValue(body.get(field), field);
        if (raw.isEmpty()) {
            throw new WiredApiException(400, WiredApiException.BATCH_EMPTY, "No operations given.");
        }
        if (raw.size() > call.settings().maxBatch()) {
            throw new WiredApiException(
                    400,
                    WiredApiException.BATCH_LIMIT_EXCEEDED,
                    "At most " + call.settings().maxBatch() + " operations.");
        }
        List<Operation> operations = new ArrayList<>();
        int writes = 0;
        for (Object item : raw) {
            Map<String, Object> object = WiredApiJson.objectValue(item, field);
            Operation operation = legacy ? Operation.parseLegacy(object, scope) : Operation.parse(object, scope);
            operations.add(operation);
            if (!operation.method().equals("GET")) {
                writes++;
            }
        }
        if (writes > 0 && !call.session().canWrite()) {
            throw WiredApiException.keyMissing();
        }

        call.writes(writes);
        JsonArray results = new JsonArray();
        for (Operation operation : operations) {
            JsonObject result = new JsonObject();
            if (operation.opId() == null) {
                result.add("op_id", JsonNull.INSTANCE);
            } else {
                result.addProperty("op_id", operation.opId());
            }
            try {
                Entry entry = operation.apply(call.room(), variable);
                if (entry == null) {
                    result.addProperty("status", 204);
                } else {
                    result.addProperty("status", 200);
                    result.add("body", WiredApiJson.stored(entry));
                }
            } catch (WiredApiException e) {
                result.addProperty("status", e.status());
                result.add("error", WiredApiJson.errorObject(e));
            }
            results.add(result);
        }
        JsonObject response = new JsonObject();
        response.add("results", results);
        return ok(response);
    }

    static WiredApiResponse getGlobal(Call call) {
        Variable variable = variable(call.room(), Scope.GLOBAL, call.params().get("variableName"));
        return ok(WiredApiJson.stored(call.room().global(variable)));
    }

    static WiredApiResponse patchGlobal(Call call) {
        Variable variable = variable(call.room(), Scope.GLOBAL, call.params().get("variableName"));
        Change change = change(WiredApiJson.parseObject(call.request().body()), variable, false);
        int next = change.apply(call.room().global(variable).value());
        call.writes(1);
        call.room().updateGlobal(variable, next);
        return ok(WiredApiJson.stored(call.room().global(variable)));
    }

    static WiredApiResponse userProfileByQuery(Call call) {
        String name = call.request().queryValue("name");
        String uniqueId = call.request().queryValue("unique_id");
        if ((name == null) == (uniqueId == null)) {
            throw WiredApiException.badRequest("Give exactly one of 'name' or 'unique_id'.");
        }
        int userId;
        if (name != null) {
            if (name.isEmpty() || name.length() > 64) {
                throw WiredApiException.badRequest("Invalid 'name'.");
            }
            userId = call.room().userIdByName(name);
            if (userId <= 0) {
                throw WiredApiException.entityNotFound();
            }
        } else {
            // A unique id here is the user id; Habbo's own ids (hhes-...) name nobody on this hotel.
            if (!WiredApiRouter.isPositiveInt(uniqueId)) {
                throw WiredApiException.entityNotFound();
            }
            userId = Integer.parseInt(uniqueId);
        }
        int id = userId;
        return call.room().atomically(TargetKind.USERS, id, () -> {
            requireHolder(call.room(), TargetKind.USERS, id);
            return ok(profile(call.room(), TargetKind.USERS, id));
        });
    }

    static WiredApiResponse getProfile(Call call, Scope scope) {
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        return call.room().atomically(kind, entityId, () -> {
            requireHolder(call.room(), kind, entityId);
            return ok(profile(call.room(), kind, entityId));
        });
    }

    static WiredApiResponse patchProfile(Call call, Scope scope) {
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        Map<Variable, Object> changes = profileChanges(call, scope);
        return call.room().atomically(kind, entityId, () -> {
            requireHolder(call.room(), kind, entityId);
            call.writes(changes.size());
            for (Map.Entry<Variable, Object> change : changes.entrySet()) {
                Variable variable = change.getKey();
                Object value = change.getValue();
                if (value == WiredApiJson.NULL) {
                    if (call.room().entry(variable, kind, entityId) != null) {
                        call.room().remove(variable, kind, entityId);
                    }
                } else if (Boolean.TRUE.equals(value)) {
                    if (call.room().entry(variable, kind, entityId) == null) {
                        call.room().assign(variable, kind, entityId, null);
                    }
                } else {
                    call.room().assign(variable, kind, entityId, (Integer) value);
                }
            }
            return ok(profile(call.room(), kind, entityId));
        });
    }

    static WiredApiResponse deleteUserProfile(Call call) {
        noBody(call);
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        return call.room().atomically(kind, entityId, () -> {
            requireHolder(call.room(), kind, entityId);
            List<Variable> held =
                    new ArrayList<>(call.room().entries(kind, entityId).keySet());
            call.writes(held.size());
            for (Variable variable : held) {
                call.room().remove(variable, kind, entityId);
            }
            return WiredApiResponse.empty(204);
        });
    }

    static WiredApiResponse getGlobalProfile(Call call) {
        return ok(globalProfile(call.room()));
    }

    static WiredApiResponse patchGlobalProfile(Call call) {
        Map<Variable, Object> changes = profileChanges(call, Scope.GLOBAL);
        call.writes(changes.size());
        for (Map.Entry<Variable, Object> change : changes.entrySet()) {
            call.room().updateGlobal(change.getKey(), (Integer) change.getValue());
        }
        return ok(globalProfile(call.room()));
    }

    private static Map<Variable, Object> profileChanges(Call call, Scope scope) {
        Map<String, Object> body = WiredApiJson.parseObject(call.request().body());
        WiredApiJson.allowOnly(body, Set.of("variables"));
        if (!body.containsKey("variables")) {
            throw WiredApiException.badRequest("'variables' is required.");
        }
        Map<String, Object> raw = WiredApiJson.objectValue(body.get("variables"), "variables");
        if (raw.isEmpty() || raw.size() > call.settings().maxBatch()) {
            throw WiredApiException.badRequest(
                    "'variables' must hold 1 to " + call.settings().maxBatch() + " variables.");
        }
        Map<Variable, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String name = entry.getKey();
            Variable variable = variable(call.room(), scope, name);
            Object value = entry.getValue();
            if (WiredApiJson.isValue(value)) {
                checkValueShape(variable, true);
                changes.put(variable, WiredApiJson.value(value, name));
            } else if (scope != Scope.GLOBAL && value == WiredApiJson.NULL) {
                changes.put(variable, WiredApiJson.NULL);
            } else if (scope != Scope.GLOBAL && Boolean.TRUE.equals(value)) {
                checkValueShape(variable, false);
                changes.put(variable, Boolean.TRUE);
            } else {
                throw WiredApiException.invalidValue(
                        scope == Scope.GLOBAL
                                ? "'" + name + "' must be a whole number."
                                : "'" + name + "' must be a whole number, null or true.");
            }
        }
        return changes;
    }

    /** Habbo's profile: the owner under its kind's key ({@code user}, {@code furni}, ...) and the values. */
    private static JsonObject profile(VariableRoom room, TargetKind kind, int entityId) {
        JsonObject variables = new JsonObject();
        for (Map.Entry<Variable, Entry> entry : room.entries(kind, entityId).entrySet()) {
            variables.add(entry.getKey().name(), WiredApiJson.stored(entry.getValue()));
        }
        JsonObject owner = new JsonObject();
        owner.addProperty("id", entityId);
        if (kind.scope() == Scope.USER) {
            String name = room.holderName(kind, entityId);
            if (name != null) {
                owner.addProperty("name", name);
            }
            if (kind == TargetKind.USERS) {
                owner.addProperty("unique_id", Integer.toString(entityId));
            }
        }
        JsonObject body = new JsonObject();
        body.add(kind.profileKey(), owner);
        body.add("variables", variables);
        return body;
    }

    private static JsonObject globalProfile(VariableRoom room) {
        JsonObject variables = new JsonObject();
        for (Variable variable : room.variables()) {
            if (variable.scope() == Scope.GLOBAL) {
                variables.add(variable.name(), WiredApiJson.stored(room.global(variable)));
            }
        }
        JsonObject body = new JsonObject();
        body.add("variables", variables);
        return body;
    }

    private static Variable findVariable(VariableRoom room, Scope scope, String name) {
        for (Variable variable : room.variables()) {
            if ((scope == null || variable.scope() == scope) && variable.name().equals(name)) {
                return variable;
            }
        }
        return null;
    }

    /** The variable of this scope (any scope when null) with exactly this name. */
    static Variable variable(VariableRoom room, Scope scope, String name) {
        Variable variable = HotelWiredApiRooms.NAME.matcher(name).matches() ? findVariable(room, scope, name) : null;
        if (variable == null) {
            throw WiredApiException.notFound("No such variable.");
        }
        return variable;
    }

    /**
     * A user who is neither in the room nor has saved values in it is not taking part (also for an id
     * nobody has); a pet, bot or item that is not there does not exist.
     */
    private static void requireHolder(VariableRoom room, TargetKind kind, int entityId) {
        if (room.holderExists(kind, entityId)) {
            return;
        }
        if (kind == TargetKind.USERS) {
            throw WiredApiException.forbidden(
                    WiredApiException.USER_NOT_PARTICIPATING, "The user does not take part in this room.");
        }
        throw WiredApiException.entityNotFound();
    }

    private static Entry assign(VariableRoom room, Variable variable, TargetKind kind, int entityId, Integer value) {
        room.assign(variable, kind, entityId, value);
        Entry entry = room.entry(variable, kind, entityId);
        if (entry == null) {
            throw WiredApiException.conflict("The value was not stored.");
        }
        return entry;
    }

    /** Applies a PATCH to a value the holder has; a null change leaves a value-less variable as it is. */
    private static Entry update(VariableRoom room, Variable variable, TargetKind kind, int entityId, Change change) {
        Entry existing = room.entry(variable, kind, entityId);
        if (existing == null) {
            throw notHeld();
        }
        if (change != null) {
            room.update(variable, kind, entityId, change.apply(existing.value()));
        }
        Entry entry = room.entry(variable, kind, entityId);
        if (entry == null) {
            throw notHeld();
        }
        return entry;
    }

    private static void checkValueShape(Variable variable, boolean valueGiven) {
        if (variable.hasValue() && !valueGiven) {
            throw WiredApiException.invalidValue("This variable has a value; 'value' is required.");
        }
        if (!variable.hasValue() && valueGiven) {
            throw WiredApiException.invalidValue("This variable has no value.");
        }
    }

    /**
     * A PATCH body: Habbo's {@code value}, or the Polaris {@code add}; null when the variable has no
     * value to change.
     */
    private static Change change(Map<String, Object> body, Variable variable, boolean allowEmpty) {
        WiredApiJson.allowOnly(body, Set.of("value", "add"));
        boolean hasSet = body.containsKey("value");
        boolean hasAdd = body.containsKey("add");
        if (hasSet && hasAdd) {
            throw WiredApiException.invalidValue("Give either 'value' or 'add', not both.");
        }
        if (!variable.hasValue()) {
            if (hasSet || hasAdd || !allowEmpty) {
                throw WiredApiException.invalidValue("This variable has no value.");
            }
            return null;
        }
        if (!hasSet && !hasAdd) {
            throw WiredApiException.invalidValue("Give 'value'.");
        }
        return hasSet
                ? new Change(WiredApiJson.value(body.get("value"), "value"), false)
                : new Change(WiredApiJson.value(body.get("add"), "add"), true);
    }

    private record Change(int amount, boolean relative) {
        int apply(Integer current) {
            if (!this.relative) {
                return this.amount;
            }
            try {
                return Math.addExact(current == null ? 0 : current, this.amount);
            } catch (ArithmeticException e) {
                throw WiredApiException.invalidValue("The result does not fit a 32-bit integer.");
            }
        }
    }

    /**
     * One batch operation: Habbo's {@code {op_id, method, path, body}}, or the older Polaris
     * {@code {op, targetKind, entityId, value}}. {@code body} is the PUT or PATCH body.
     */
    private record Operation(String opId, String method, TargetKind kind, int entityId, Map<String, Object> body) {
        static Operation parse(Map<String, Object> raw, Scope scope) {
            WiredApiJson.allowOnly(raw, Set.of("op_id", "method", "path", "body"));
            String opId = null;
            Object rawOpId = raw.get("op_id");
            if (rawOpId != null && rawOpId != WiredApiJson.NULL) {
                opId = WiredApiJson.stringValue(rawOpId, "op_id");
                if (opId.length() > MAX_OP_ID) {
                    throw WiredApiException.badRequest("'op_id' is too long.");
                }
            }
            String method = WiredApiJson.stringValue(raw.get("method"), "method");
            if (!METHODS.contains(method)) {
                throw WiredApiException.badRequest("'method' must be GET, PUT, PATCH or DELETE.");
            }
            String path = WiredApiJson.stringValue(raw.get("path"), "path");
            int slash = path.indexOf('/');
            TargetKind kind = slash > 0 ? TargetKind.fromPath(path.substring(0, slash)) : null;
            String id = slash > 0 ? path.substring(slash + 1) : "";
            if (kind == null || kind.scope() != scope || !WiredApiRouter.isPositiveInt(id)) {
                throw WiredApiException.invalidTarget("'path' must be <targetKind>/<entityId> for this scope.");
            }
            Map<String, Object> body = null;
            Object rawBody = raw.get("body");
            if (rawBody != null && rawBody != WiredApiJson.NULL) {
                if (method.equals("GET") || method.equals("DELETE")) {
                    throw WiredApiException.badRequest("'" + method + "' takes no body.");
                }
                body = WiredApiJson.objectValue(rawBody, "body");
                WiredApiJson.allowOnly(body, method.equals("PATCH") ? Set.of("value", "add") : Set.of("value"));
                for (Object value : body.values()) {
                    WiredApiJson.value(value, "value");
                }
            }
            return new Operation(opId, method, kind, Integer.parseInt(id), body == null ? Map.of() : body);
        }

        static Operation parseLegacy(Map<String, Object> raw, Scope scope) {
            WiredApiJson.allowOnly(raw, Set.of("op", "targetKind", "entityId", "value"));
            String op = WiredApiJson.stringValue(raw.get("op"), "op");
            if (!Set.of("set", "add", "delete").contains(op)) {
                throw WiredApiException.badRequest("'op' must be set, add or delete.");
            }
            TargetKind kind = raw.get("targetKind") instanceof String text ? TargetKind.fromPath(text) : null;
            if (kind == null || kind.scope() != scope) {
                throw WiredApiException.invalidTarget("Invalid 'targetKind' for this scope.");
            }
            Object rawId = raw.get("entityId");
            if (!(rawId instanceof WiredApiJson.JsonNumber number) || !WiredApiRouter.isPositiveInt(number.literal())) {
                throw WiredApiException.invalidTarget("'entityId' must be a positive integer.");
            }
            Object value = raw.get("value");
            if (value != null) {
                WiredApiJson.value(value, "value");
            }
            if (op.equals("delete") && value != null) {
                throw WiredApiException.badRequest("'delete' takes no value.");
            }
            if (op.equals("add") && value == null) {
                throw WiredApiException.invalidValue("'add' needs a value.");
            }
            int entityId = Integer.parseInt(number.literal());
            return switch (op) {
                case "set" ->
                    new Operation(null, "PUT", kind, entityId, value == null ? Map.of() : Map.of("value", value));
                case "add" -> new Operation(null, "PATCH", kind, entityId, Map.of("add", value));
                default -> new Operation(null, "DELETE", kind, entityId, Map.of());
            };
        }

        /** The stored value afterwards, or null for a delete. */
        Entry apply(VariableRoom room, Variable variable) {
            return room.atomically(this.kind, this.entityId, () -> this.applyNow(room, variable));
        }

        private Entry applyNow(VariableRoom room, Variable variable) {
            requireHolder(room, this.kind, this.entityId);
            switch (this.method) {
                case "GET" -> {
                    Entry entry = room.entry(variable, this.kind, this.entityId);
                    if (entry == null) {
                        throw notHeld();
                    }
                    return entry;
                }
                case "PUT" -> {
                    Integer value =
                            this.body.containsKey("value") ? WiredApiJson.value(this.body.get("value"), "value") : null;
                    checkValueShape(variable, value != null);
                    return assign(room, variable, this.kind, this.entityId, value);
                }
                case "PATCH" -> {
                    return update(room, variable, this.kind, this.entityId, change(this.body, variable, true));
                }
                default -> {
                    if (room.entry(variable, this.kind, this.entityId) == null) {
                        throw notHeld();
                    }
                    room.remove(variable, this.kind, this.entityId);
                    return null;
                }
            }
        }
    }

    /** One of Habbo's query parameter and its older Polaris name; giving both is refused. */
    private static String either(WiredApiRequest request, String name, String alias) {
        String value = request.queryValue(name);
        String old = request.queryValue(alias);
        if (value != null && old != null) {
            throw WiredApiException.badRequest("Give '" + name + "' or '" + alias + "', not both.");
        }
        return value != null ? value : old;
    }

    private static int pageParam(String raw, int fallback, int max, String name) {
        if (raw == null) {
            return fallback;
        }
        if (!PAGE.matcher(raw).matches() || Integer.parseInt(raw) > max) {
            throw WiredApiException.badRequest("'" + name + "' must be between 1 and " + max + ".");
        }
        return Integer.parseInt(raw);
    }

    private static void noBody(Call call) {
        if (call.request().body() != null && call.request().body().length > 0) {
            throw WiredApiException.badRequest("This endpoint takes no body.");
        }
    }

    private static WiredApiException notHeld() {
        return WiredApiException.notFound("The holder has no stored value for this variable.");
    }

    private static WiredApiResponse ok(JsonObject body) {
        return WiredApiResponse.json(200, body.toString());
    }
}
