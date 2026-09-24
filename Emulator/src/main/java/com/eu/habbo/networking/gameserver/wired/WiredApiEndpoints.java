package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Entry;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Scope;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.TargetKind;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Variable;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.VariableRoom;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** The operations behind each route. Paths and keys are already validated by the router. */
final class WiredApiEndpoints {
    private static final Pattern PAGE = Pattern.compile("[1-9][0-9]{0,6}");
    private static final int DEFAULT_PAGE_SIZE = 50;

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
        JsonArray variables = new JsonArray();
        for (Variable variable : call.room().variables()) {
            variables.add(WiredApiJson.variable(variable));
        }
        JsonObject body = new JsonObject();
        body.add("variables", variables);
        return ok(body);
    }

    static WiredApiResponse getEntry(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        Entry entry = call.room().entry(variable, call.kind(), call.intParam("entityId"));
        if (entry == null) {
            throw notHeld();
        }
        return ok(WiredApiJson.entry(entry, true));
    }

    static WiredApiResponse putEntry(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        Map<String, Object> body = WiredApiJson.parseObject(call.request().body());
        WiredApiJson.allowOnly(body, Set.of("value"));
        Integer value = body.containsKey("value") ? WiredApiJson.intValue(body.get("value"), "value") : null;
        checkValueShape(variable, value != null);

        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        if (!call.room().holderExists(kind, entityId)) {
            throw unknownHolder();
        }
        call.writes(1);
        call.room().assign(variable, kind, entityId, value);
        Entry entry = call.room().entry(variable, kind, entityId);
        if (entry == null) {
            throw WiredApiException.conflict("The value was not stored.");
        }
        return ok(WiredApiJson.entry(entry, true));
    }

    static WiredApiResponse patchEntry(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        Change change = change(WiredApiJson.parseObject(call.request().body()), variable, true);
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        Entry existing = call.room().entry(variable, kind, entityId);
        if (existing == null) {
            throw notHeld();
        }
        if (change != null) {
            int next = change.apply(existing.value());
            call.writes(1);
            call.room().update(variable, kind, entityId, next);
        }
        Entry entry = call.room().entry(variable, kind, entityId);
        if (entry == null) {
            throw notHeld();
        }
        return ok(WiredApiJson.entry(entry, true));
    }

    static WiredApiResponse deleteEntry(Call call) {
        noBody(call);
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        if (call.room().entry(variable, kind, entityId) == null) {
            throw notHeld();
        }
        call.writes(1);
        call.room().remove(variable, kind, entityId);
        return WiredApiResponse.empty(204);
    }

    static WiredApiResponse listEntries(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        int page = pageParam(call.request().queryValue("page"), 1, 1_000_000, "page");
        int pageSize = pageParam(
                call.request().queryValue("pageSize"),
                Math.min(DEFAULT_PAGE_SIZE, call.settings().maxPageSize()),
                call.settings().maxPageSize(),
                "pageSize");
        String sort = choice(call.request().queryValue("sort"), "entityId", Set.of("entityId", "value"), "sort");
        String order = choice(call.request().queryValue("order"), "asc", Set.of("asc", "desc"), "order");

        List<Entry> entries = new ArrayList<>(call.room().holders(variable, call.kind()));
        Comparator<Entry> comparator = sort.equals("value")
                ? Comparator.comparingInt((Entry entry) -> entry.value() == null ? 0 : entry.value())
                        .thenComparingInt(Entry::entityId)
                : Comparator.comparingInt(Entry::entityId);
        entries.sort(order.equals("desc") ? comparator.reversed() : comparator);

        int from = (int) Math.min((long) (page - 1) * pageSize, entries.size());
        int to = Math.min(from + pageSize, entries.size());
        JsonArray items = new JsonArray();
        for (Entry entry : entries.subList(from, to)) {
            items.add(WiredApiJson.entry(entry, true));
        }
        JsonObject body = new JsonObject();
        body.addProperty("page", page);
        body.addProperty("pageSize", pageSize);
        body.addProperty("total", entries.size());
        body.add("entries", items);
        return ok(body);
    }

    static WiredApiResponse countEntries(Call call) {
        Variable variable = variable(call.room(), call.scope(), call.params().get("variableName"));
        JsonObject body = new JsonObject();
        body.addProperty("count", call.room().holders(variable, call.kind()).size());
        return ok(body);
    }

    static WiredApiResponse bulkDelete(Call call) {
        Map<String, Object> body = WiredApiJson.parseObject(call.request().body());
        WiredApiJson.allowOnly(body, Set.of("names"));
        if (!body.containsKey("names")) {
            throw WiredApiException.badRequest("'names' is required.");
        }
        List<Object> names = WiredApiJson.arrayValue(body.get("names"), "names");
        if (names.isEmpty() || names.size() > call.settings().maxBulkNames()) {
            throw WiredApiException.badRequest(
                    "'names' must hold 1 to " + call.settings().maxBulkNames() + " names.");
        }
        List<Variable> variables = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object raw : names) {
            String name = WiredApiJson.stringValue(raw, "names");
            if (!HotelWiredApiRooms.NAME.matcher(name).matches()) {
                throw WiredApiException.badRequest("Invalid variable name '" + WiredApiJson.safe(name) + "'.");
            }
            if (!seen.add(name)) {
                throw WiredApiException.badRequest("Duplicate variable name '" + name + "'.");
            }
            variables.add(variable(call.room(), null, name));
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
        WiredApiJson.allowOnly(body, Set.of("operations"));
        if (!body.containsKey("operations")) {
            throw WiredApiException.badRequest("'operations' is required.");
        }
        List<Object> raw = WiredApiJson.arrayValue(body.get("operations"), "operations");
        if (raw.isEmpty() || raw.size() > call.settings().maxBatch()) {
            throw WiredApiException.badRequest(
                    "'operations' must hold 1 to " + call.settings().maxBatch() + " operations.");
        }
        List<Operation> operations = new ArrayList<>();
        for (Object item : raw) {
            operations.add(Operation.parse(WiredApiJson.objectValue(item, "operations"), scope));
        }

        call.writes(operations.size());
        JsonArray results = new JsonArray();
        for (Operation operation : operations) {
            JsonObject result = new JsonObject();
            try {
                Entry entry = operation.apply(call.room(), variable);
                result.addProperty("ok", true);
                if (entry != null) {
                    result.add("entry", WiredApiJson.entry(entry, true));
                }
            } catch (WiredApiException e) {
                result.addProperty("ok", false);
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
        return ok(WiredApiJson.entry(call.room().global(variable), false));
    }

    static WiredApiResponse patchGlobal(Call call) {
        Variable variable = variable(call.room(), Scope.GLOBAL, call.params().get("variableName"));
        Change change = change(WiredApiJson.parseObject(call.request().body()), variable, false);
        int next = change.apply(call.room().global(variable).value());
        call.writes(1);
        call.room().updateGlobal(variable, next);
        return ok(WiredApiJson.entry(call.room().global(variable), false));
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
        } else {
            userId = WiredApiRouter.positiveInt(uniqueId, "unique_id");
        }
        if (userId <= 0 || !call.room().holderExists(TargetKind.USERS, userId)) {
            throw unknownHolder();
        }
        return ok(profile(call.room(), Scope.USER, TargetKind.USERS, userId));
    }

    static WiredApiResponse getProfile(Call call, Scope scope) {
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        if (!call.room().holderExists(kind, entityId)) {
            throw unknownHolder();
        }
        return ok(profile(call.room(), scope, kind, entityId));
    }

    static WiredApiResponse patchProfile(Call call, Scope scope) {
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        Map<Variable, Object> changes = profileChanges(call, scope);
        if (!call.room().holderExists(kind, entityId)) {
            throw unknownHolder();
        }
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
        return ok(profile(call.room(), scope, kind, entityId));
    }

    static WiredApiResponse deleteUserProfile(Call call) {
        noBody(call);
        TargetKind kind = call.kind();
        int entityId = call.intParam("entityId");
        if (!call.room().holderExists(kind, entityId)) {
            throw unknownHolder();
        }
        List<Variable> held = new ArrayList<>();
        for (Variable variable : call.room().variables()) {
            if (variable.scope() == Scope.USER && call.room().entry(variable, kind, entityId) != null) {
                held.add(variable);
            }
        }
        call.writes(held.size());
        for (Variable variable : held) {
            call.room().remove(variable, kind, entityId);
        }
        return WiredApiResponse.empty(204);
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
            if (!HotelWiredApiRooms.NAME.matcher(name).matches()) {
                throw WiredApiException.badRequest("Invalid variable name '" + WiredApiJson.safe(name) + "'.");
            }
            Variable variable = variable(call.room(), scope, name);
            Object value = entry.getValue();
            if (WiredApiJson.isInteger(value)) {
                checkValueShape(variable, true);
                changes.put(variable, WiredApiJson.intValue(value, name));
            } else if (scope != Scope.GLOBAL && value == WiredApiJson.NULL) {
                changes.put(variable, WiredApiJson.NULL);
            } else if (scope != Scope.GLOBAL && Boolean.TRUE.equals(value)) {
                checkValueShape(variable, false);
                changes.put(variable, Boolean.TRUE);
            } else {
                throw WiredApiException.badRequest(
                        scope == Scope.GLOBAL
                                ? "'" + name + "' must be a 32-bit integer."
                                : "'" + name + "' must be a 32-bit integer, null or true.");
            }
        }
        return changes;
    }

    private static JsonObject profile(VariableRoom room, Scope scope, TargetKind kind, int entityId) {
        JsonObject variables = new JsonObject();
        for (Variable variable : room.variables()) {
            if (variable.scope() != scope) {
                continue;
            }
            Entry entry = room.entry(variable, kind, entityId);
            if (entry != null) {
                variables.add(variable.name(), WiredApiJson.entry(entry, false));
            }
        }
        JsonObject body = new JsonObject();
        body.addProperty("targetKind", kind.path());
        body.addProperty("entityId", entityId);
        if (scope == Scope.USER) {
            String name = room.holderName(kind, entityId);
            if (name != null) {
                body.addProperty("name", name);
            }
        }
        body.add("variables", variables);
        return body;
    }

    private static JsonObject globalProfile(VariableRoom room) {
        JsonObject variables = new JsonObject();
        for (Variable variable : room.variables()) {
            if (variable.scope() == Scope.GLOBAL) {
                variables.add(variable.name(), WiredApiJson.entry(room.global(variable), false));
            }
        }
        JsonObject body = new JsonObject();
        body.addProperty("targetKind", "global");
        body.addProperty("entityId", room.id());
        body.add("variables", variables);
        return body;
    }

    /** The variable of this scope (any scope when null) with exactly this name. */
    static Variable variable(VariableRoom room, Scope scope, String name) {
        for (Variable variable : room.variables()) {
            if ((scope == null || variable.scope() == scope) && variable.name().equals(name)) {
                return variable;
            }
        }
        throw WiredApiException.notFound("Unknown variable '" + WiredApiJson.safe(name) + "'.");
    }

    private static void checkValueShape(Variable variable, boolean valueGiven) {
        if (variable.hasValue() && !valueGiven) {
            throw WiredApiException.badRequest("This variable has a value; 'value' is required.");
        }
        if (!variable.hasValue() && valueGiven) {
            throw WiredApiException.badRequest("This variable has no value.");
        }
    }

    /** A PATCH body: a new value or an amount to add; null when the variable has no value to change. */
    private static Change change(Map<String, Object> body, Variable variable, boolean allowEmpty) {
        WiredApiJson.allowOnly(body, Set.of("value", "add"));
        boolean hasSet = body.containsKey("value");
        boolean hasAdd = body.containsKey("add");
        if (hasSet && hasAdd) {
            throw WiredApiException.badRequest("Give either 'value' or 'add', not both.");
        }
        if (!variable.hasValue()) {
            if (hasSet || hasAdd || !allowEmpty) {
                throw WiredApiException.badRequest("This variable has no value.");
            }
            return null;
        }
        if (!hasSet && !hasAdd) {
            throw WiredApiException.badRequest("Give 'value' or 'add'.");
        }
        return hasSet
                ? new Change(WiredApiJson.intValue(body.get("value"), "value"), false)
                : new Change(WiredApiJson.intValue(body.get("add"), "add"), true);
    }

    private record Change(int amount, boolean relative) {
        int apply(Integer current) {
            if (!this.relative) {
                return this.amount;
            }
            try {
                return Math.addExact(current == null ? 0 : current, this.amount);
            } catch (ArithmeticException e) {
                throw WiredApiException.badRequest("The result does not fit a 32-bit integer.");
            }
        }
    }

    private record Operation(String op, TargetKind kind, int entityId, Integer value) {
        static Operation parse(Map<String, Object> raw, Scope scope) {
            WiredApiJson.allowOnly(raw, Set.of("op", "targetKind", "entityId", "value"));
            String op = WiredApiJson.stringValue(raw.get("op"), "op");
            if (!Set.of("set", "add", "delete").contains(op)) {
                throw WiredApiException.badRequest("'op' must be set, add or delete.");
            }
            TargetKind kind = TargetKind.fromPath(WiredApiJson.stringValue(raw.get("targetKind"), "targetKind"));
            if (kind == null || kind.scope() != scope) {
                throw WiredApiException.badRequest("Invalid 'targetKind' for this scope.");
            }
            int entityId = WiredApiJson.intValue(raw.get("entityId"), "entityId");
            if (entityId <= 0) {
                throw WiredApiException.badRequest("'entityId' must be a positive integer.");
            }
            Integer value = raw.containsKey("value") ? WiredApiJson.intValue(raw.get("value"), "value") : null;
            if (op.equals("delete") && value != null) {
                throw WiredApiException.badRequest("'delete' takes no value.");
            }
            if (op.equals("add") && value == null) {
                throw WiredApiException.badRequest("'add' needs a value.");
            }
            return new Operation(op, kind, entityId, value);
        }

        Entry apply(VariableRoom room, Variable variable) {
            switch (this.op) {
                case "set" -> {
                    checkValueShape(variable, this.value != null);
                    if (!room.holderExists(this.kind, this.entityId)) {
                        throw unknownHolder();
                    }
                    room.assign(variable, this.kind, this.entityId, this.value);
                    Entry entry = room.entry(variable, this.kind, this.entityId);
                    if (entry == null) {
                        throw WiredApiException.conflict("The value was not stored.");
                    }
                    return entry;
                }
                case "add" -> {
                    if (!variable.hasValue()) {
                        throw WiredApiException.badRequest("This variable has no value.");
                    }
                    Entry existing = room.entry(variable, this.kind, this.entityId);
                    if (existing == null) {
                        throw notHeld();
                    }
                    room.update(
                            variable, this.kind, this.entityId, new Change(this.value, true).apply(existing.value()));
                    Entry entry = room.entry(variable, this.kind, this.entityId);
                    if (entry == null) {
                        throw notHeld();
                    }
                    return entry;
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

    private static int pageParam(String raw, int fallback, int max, String name) {
        if (raw == null) {
            return fallback;
        }
        if (!PAGE.matcher(raw).matches() || Integer.parseInt(raw) > max) {
            throw WiredApiException.badRequest("'" + name + "' must be between 1 and " + max + ".");
        }
        return Integer.parseInt(raw);
    }

    private static String choice(String raw, String fallback, Set<String> allowed, String name) {
        if (raw == null) {
            return fallback;
        }
        if (!allowed.contains(raw)) {
            throw WiredApiException.badRequest("Invalid '" + name + "'.");
        }
        return raw;
    }

    private static void noBody(Call call) {
        if (call.request().body() != null && call.request().body().length > 0) {
            throw WiredApiException.badRequest("This endpoint takes no body.");
        }
    }

    private static WiredApiException notHeld() {
        return WiredApiException.notFound("The holder does not have this variable.");
    }

    private static WiredApiException unknownHolder() {
        return WiredApiException.notFound("No such holder in this room.");
    }

    private static WiredApiResponse ok(JsonObject body) {
        return WiredApiResponse.json(200, body.toString());
    }
}
