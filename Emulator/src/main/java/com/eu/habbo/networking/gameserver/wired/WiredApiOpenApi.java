package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.networking.gameserver.wired.WiredApiAuth.Level;
import com.eu.habbo.networking.gameserver.wired.WiredApiRouter.Route;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** The OpenAPI 3 document and a plain HTML page of it, both built from the route table. */
final class WiredApiOpenApi {
    private static final String NAME_PATTERN = "^[A-Za-z0-9_]{1,40}$";
    private static final List<String[]> ERRORS = List.of(
            new String[] {"400", "Invalid target, value or request."},
            new String[] {"403", "Key missing or invalid, API disabled for the room, or not allowed."},
            new String[] {"404", "Unknown room, variable or holder."},
            new String[] {"413", "Body too large."},
            new String[] {"429", "Too many requests; see Retry-After."});
    private static final String[] ERROR_CODES = {
        WiredApiException.INVALID_TARGET,
        WiredApiException.INVALID_VALUE,
        WiredApiException.BULK_DELETE_EMPTY,
        WiredApiException.BULK_DELETE_INVALID_VARIABLE,
        WiredApiException.BATCH_EMPTY,
        WiredApiException.BATCH_LIMIT_EXCEEDED,
        WiredApiException.KEY_MISSING,
        WiredApiException.KEY_INVALID,
        WiredApiException.API_DISABLED,
        WiredApiException.USER_NOT_PARTICIPATING,
        WiredApiException.BULK_DELETE_NOT_ENABLED,
        WiredApiException.ROOM_NOT_FOUND,
        WiredApiException.NOT_FOUND,
        WiredApiException.ENTITY_NOT_FOUND,
        WiredApiException.TOO_MANY_REQUESTS,
        WiredApiException.INVALID_REQUEST,
        WiredApiException.BULK_DELETE_LIMIT_EXCEEDED,
        WiredApiException.UNKNOWN_ENDPOINT,
        WiredApiException.METHOD_NOT_ALLOWED,
        WiredApiException.PAYLOAD_TOO_LARGE,
        WiredApiException.CONFLICT,
        WiredApiException.INTERNAL_ERROR
    };

    private WiredApiOpenApi() {}

    static JsonObject document(List<Route> routes) {
        JsonObject root = new JsonObject();
        root.addProperty("openapi", "3.0.3");
        JsonObject info = new JsonObject();
        info.addProperty("title", "Variables Web API");
        info.addProperty("version", "1");
        info.addProperty(
                "description",
                "Read and write a room's permanent wired variables, in the format of Habbo's Wired Variables"
                        + " API. Keys come from the room's Variables Web API box and go in the X-Wired-Read-Key and"
                        + " X-Wired-Write-Key headers, never in the URL.");
        root.add("info", info);
        JsonArray servers = new JsonArray();
        JsonObject server = new JsonObject();
        server.addProperty("url", WiredApiRouter.PREFIX);
        servers.add(server);
        root.add("servers", servers);

        JsonObject paths = new JsonObject();
        for (Route route : routes) {
            if (route.level() == Level.NONE) {
                continue;
            }
            JsonObject item = paths.has(route.path()) ? paths.getAsJsonObject(route.path()) : new JsonObject();
            item.add(route.method().toLowerCase(java.util.Locale.ROOT), operation(route));
            paths.add(route.path(), item);
        }
        root.add("paths", paths);
        root.add("components", components());
        return root;
    }

    private static JsonObject operation(Route route) {
        JsonObject operation = new JsonObject();
        operation.addProperty("summary", route.summary());
        operation.addProperty(
                "description",
                route.path().endsWith("/batch")
                        ? "X-Wired-Read-Key; operations that write also need X-Wired-Write-Key."
                        : switch (route.level()) {
                            case READ -> "X-Wired-Read-Key (the write key also works).";
                            case WRITE -> "X-Wired-Write-Key.";
                            case BULK -> "X-Wired-Write-Key, and the box must allow bulk delete.";
                            case NONE -> "No key.";
                        });
        JsonArray parameters = new JsonArray();
        for (String segment : route.segments()) {
            if (segment.startsWith("{")) {
                parameters.add(pathParameter(segment.substring(1, segment.length() - 1), route.path()));
            }
        }
        for (String query : route.query().stream().sorted().toList()) {
            parameters.add(queryParameter(query));
        }
        operation.add("parameters", parameters);
        if (route.requestSchema() != null) {
            JsonObject body = new JsonObject();
            body.addProperty("required", true);
            body.add("content", jsonContent(route.requestSchema()));
            operation.add("requestBody", body);
        }
        JsonObject responses = new JsonObject();
        JsonObject success = new JsonObject();
        if (route.responseSchema() == null) {
            success.addProperty("description", "Done.");
            responses.add("204", success);
        } else {
            success.addProperty("description", "Success.");
            success.add("content", jsonContent(route.responseSchema()));
            responses.add("200", success);
        }
        for (String[] error : ERRORS) {
            JsonObject response = new JsonObject();
            response.addProperty("description", error[1]);
            response.add("content", jsonContent("Error"));
            responses.add(error[0], response);
        }
        operation.add("responses", responses);
        JsonArray security = new JsonArray();
        JsonObject habbo = new JsonObject();
        if (route.path().endsWith("/batch") || route.level() == Level.READ) {
            habbo.add("readKey", new JsonArray());
        }
        if (route.path().endsWith("/batch") || route.level() != Level.READ) {
            habbo.add("writeKey", new JsonArray());
        }
        security.add(habbo);
        JsonObject bearer = new JsonObject();
        bearer.add("bearerKey", new JsonArray());
        security.add(bearer);
        JsonObject header = new JsonObject();
        header.add("apiKeyHeader", new JsonArray());
        security.add(header);
        operation.add("security", security);
        return operation;
    }

    private static JsonObject pathParameter(String name, String path) {
        JsonObject parameter = new JsonObject();
        parameter.addProperty("name", name);
        parameter.addProperty("in", "path");
        parameter.addProperty("required", true);
        JsonObject schema = new JsonObject();
        switch (name) {
            case "roomId", "entityId" -> {
                schema.addProperty("type", "integer");
                schema.addProperty("format", "int32");
                schema.addProperty("minimum", 1);
            }
            case "variableName" -> {
                schema.addProperty("type", "string");
                schema.addProperty("pattern", NAME_PATTERN);
            }
            case "scope" -> schema.add("enum", strings("user", "furni"));
            case "targetKind" -> {
                schema.add(
                        "enum",
                        path.contains("/variables_profile/user/")
                                ? strings("users", "pets", "bots")
                                : path.contains("/variables_profile/furni/")
                                        ? strings("furni", "furni-bc", "wall-items", "wall-items-bc")
                                        : strings(
                                                "users",
                                                "pets",
                                                "bots",
                                                "furni",
                                                "furni-bc",
                                                "wall-items",
                                                "wall-items-bc"));
                schema.addProperty("description", "floor and wall are accepted for furni and wall-items.");
            }
            default -> schema.addProperty("type", "string");
        }
        if (!schema.has("type")) {
            schema.addProperty("type", "string");
        }
        parameter.add("schema", schema);
        return parameter;
    }

    private static JsonObject queryParameter(String name) {
        JsonObject parameter = new JsonObject();
        parameter.addProperty("name", name);
        parameter.addProperty("in", "query");
        parameter.addProperty("required", false);
        JsonObject schema = new JsonObject();
        switch (name) {
            case "page", "size", "pageSize" -> {
                schema.addProperty("type", "integer");
                schema.addProperty("minimum", 1);
            }
            case "order_by" -> {
                schema.addProperty("type", "string");
                schema.add("enum", strings("value", "creation_time", "update_time"));
            }
            case "sort" -> {
                schema.addProperty("type", "string");
                schema.add("enum", strings("entityId", "value"));
            }
            case "order_dir", "order" -> {
                schema.addProperty("type", "string");
                schema.add("enum", strings("asc", "desc"));
            }
            default -> schema.addProperty("type", "string");
        }
        if (Set.of("pageSize", "sort", "order").contains(name)) {
            parameter.addProperty("deprecated", true);
            parameter.addProperty("description", "Older Polaris name; use size, order_by and order_dir.");
        }
        parameter.add("schema", schema);
        return parameter;
    }

    private static JsonObject jsonContent(String schemaName) {
        JsonObject ref = new JsonObject();
        ref.addProperty("$ref", "#/components/schemas/" + schemaName);
        JsonObject media = new JsonObject();
        media.add("schema", ref);
        JsonObject content = new JsonObject();
        content.add("application/json", media);
        return content;
    }

    private static JsonObject components() {
        JsonObject schemas = new JsonObject();
        schemas.add("Error", object("error", enumString(ERROR_CODES)));
        JsonObject names = new JsonObject();
        names.addProperty("type", "array");
        names.add("items", type("string"));
        schemas.add("RoomVariables", object("users", names, "furni", names, "global", names));
        schemas.add(
                "VariableDefinition",
                object(
                        "name",
                        type("string"),
                        "scope",
                        enumString("user", "furni", "global"),
                        "has_value",
                        type("boolean"),
                        "text_connected",
                        type("boolean")));
        schemas.add("VariableDefinitions", object("variables", arrayOf("VariableDefinition")));
        JsonObject value = type("string");
        value.addProperty("pattern", "^-?[0-9]+$");
        value.addProperty("description", "The value as text; left out for a variable without a value.");
        JsonObject time = type("string");
        time.addProperty("format", "date-time");
        schemas.add("WiredVariable", object("value", value, "creation_time", time, "update_time", time));
        schemas.add(
                "PagedVariableItem",
                object(
                        "id",
                        type("integer"),
                        "name",
                        type("string"),
                        "unique_id",
                        type("string"),
                        "value",
                        value,
                        "creation_time",
                        time,
                        "update_time",
                        time));
        schemas.add(
                "PagedVariables",
                object("items", arrayOf("PagedVariableItem"), "page", type("integer"), "size", type("integer")));
        schemas.add("Count", object("count", type("integer")));
        JsonObject input = new JsonObject();
        input.addProperty("description", "A whole number, as text (\"12\") or as a number.");
        schemas.add("ValueBody", object("value", input));
        JsonObject add = type("integer");
        add.addProperty("description", "Polaris addition: added to the current value instead of value.");
        schemas.add("PatchBody", object("value", input, "add", add));
        JsonObject deleteNames = new JsonObject();
        deleteNames.addProperty("type", "array");
        deleteNames.add("items", type("string"));
        schemas.add("BulkDeleteBody", object("variables", deleteNames));
        JsonObject counts = new JsonObject();
        counts.addProperty("type", "object");
        counts.add("additionalProperties", type("integer"));
        schemas.add("BulkDeleteResult", object("deleted", counts));
        schemas.add(
                "BatchRequest",
                object(
                        "op_id",
                        type("string"),
                        "method",
                        enumString("GET", "PUT", "PATCH", "DELETE"),
                        "path",
                        type("string"),
                        "body",
                        ref("PatchBody")));
        schemas.add("BatchBody", object("requests", arrayOf("BatchRequest")));
        schemas.add("BatchError", object("code", enumString(ERROR_CODES), "message", type("string")));
        JsonObject result = object(
                "op_id",
                type("string"),
                "status",
                type("integer"),
                "body",
                ref("WiredVariable"),
                "error",
                ref("BatchError"));
        schemas.add("BatchResults", object("results", arrayOfSchema(result)));
        JsonObject values = new JsonObject();
        values.addProperty("type", "object");
        values.addProperty(
                "description",
                "Variable name to a whole number (text or number), null (delete) or true (Polaris: create"
                        + " without value).");
        schemas.add("ProfilePatchBody", object("variables", values));
        JsonObject globals = new JsonObject();
        globals.addProperty("type", "object");
        globals.addProperty("description", "Variable name to a whole number (text or number).");
        schemas.add("GlobalProfilePatchBody", object("variables", globals));
        JsonObject profileValues = new JsonObject();
        profileValues.addProperty("type", "object");
        profileValues.add("additionalProperties", ref("WiredVariable"));
        JsonObject owner = object("id", type("integer"), "name", type("string"), "unique_id", type("string"));
        JsonObject profile = object(
                "user",
                owner,
                "pet",
                owner,
                "bot",
                owner,
                "furni",
                owner,
                "furni_bc",
                owner,
                "wall_item",
                owner,
                "wall_item_bc",
                owner,
                "variables",
                profileValues);
        profile.addProperty("description", "One owner field, named after the target kind; none for the room.");
        schemas.add("Profile", profile);

        JsonObject securitySchemes = new JsonObject();
        securitySchemes.add("readKey", apiKeyHeader("X-Wired-Read-Key"));
        securitySchemes.add("writeKey", apiKeyHeader("X-Wired-Write-Key"));
        JsonObject bearer = new JsonObject();
        bearer.addProperty("type", "http");
        bearer.addProperty("scheme", "bearer");
        bearer.addProperty("description", "Older Polaris form: either key.");
        securitySchemes.add("bearerKey", bearer);
        JsonObject header = apiKeyHeader("X-Api-Key");
        header.addProperty("description", "Older Polaris form: either key.");
        securitySchemes.add("apiKeyHeader", header);

        JsonObject components = new JsonObject();
        components.add("schemas", schemas);
        components.add("securitySchemes", securitySchemes);
        return components;
    }

    private static JsonObject apiKeyHeader(String name) {
        JsonObject header = new JsonObject();
        header.addProperty("type", "apiKey");
        header.addProperty("in", "header");
        header.addProperty("name", name);
        return header;
    }

    private static JsonObject object(Object... properties) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        for (int i = 0; i < properties.length; i += 2) {
            props.add((String) properties[i], (JsonObject) properties[i + 1]);
        }
        schema.add("properties", props);
        return schema;
    }

    private static JsonObject type(String type) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        return schema;
    }

    private static JsonObject ref(String name) {
        JsonObject schema = new JsonObject();
        schema.addProperty("$ref", "#/components/schemas/" + name);
        return schema;
    }

    private static JsonObject arrayOf(String name) {
        return arrayOfSchema(ref(name));
    }

    private static JsonObject arrayOfSchema(JsonObject items) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "array");
        schema.add("items", items);
        return schema;
    }

    private static JsonObject enumString(String... values) {
        JsonObject schema = type("string");
        schema.add("enum", strings(values));
        return schema;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    static byte[] html(List<Route> routes) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Variables Web API</title><style>")
                .append("body{font-family:system-ui,sans-serif;max-width:960px;margin:0 auto;padding:16px;")
                .append("line-height:1.5;color:#1d1d1f;background:#fff}")
                .append("code{background:#f2f2f4;padding:1px 4px;border-radius:4px}")
                .append("table{border-collapse:collapse;width:100%}td,th{text-align:left;padding:6px 8px;")
                .append("border-bottom:1px solid #ddd;vertical-align:top}")
                .append(".m{font-weight:600;font-family:monospace}")
                .append("@media (prefers-color-scheme:dark){body{background:#161618;color:#eee}")
                .append("code{background:#2a2a2e}td,th{border-color:#333}}")
                .append("</style></head><body><h1>Variables Web API</h1>")
                .append("<p>Base path <code>")
                .append(escape(WiredApiRouter.PREFIX))
                .append("</code>, in the format of Habbo's Wired Variables API. Send the read key as ")
                .append("<code>X-Wired-Read-Key</code> and the write key as <code>X-Wired-Write-Key</code> ")
                .append("(<code>Authorization: Bearer</code> and <code>X-Api-Key</code> still work); ")
                .append("keys in the URL are refused. Values travel as text (<code>\"12\"</code>), ")
                .append("times as ISO 8601. Errors look like ")
                .append("<code>{\"error\":\"wired.variables.key_invalid\"}</code>. ")
                .append("The OpenAPI document is at <a href=\"")
                .append(escape(WiredApiRouter.DOCS_PATH))
                .append("\">")
                .append(escape(WiredApiRouter.DOCS_PATH))
                .append("</a>.</p><table><thead><tr><th>Method</th><th>Path</th><th>Key</th>")
                .append("<th>What it does</th></tr></thead><tbody>");
        for (Route route : routes) {
            if (route.level() == Level.NONE) {
                continue;
            }
            html.append("<tr><td class=\"m\">")
                    .append(escape(route.method()))
                    .append("</td><td><code>")
                    .append(escape(route.path()))
                    .append("</code></td><td>")
                    .append(escape(route.level().name().toLowerCase(java.util.Locale.ROOT)))
                    .append("</td><td>")
                    .append(escape(route.summary()));
            if (route.requestSchema() != null) {
                html.append(" Body: <code>")
                        .append(escape(route.requestSchema()))
                        .append("</code>.");
            }
            if (!route.query().isEmpty()) {
                html.append(" Query: <code>")
                        .append(escape(String.join(
                                ", ", route.query().stream().sorted().toList())))
                        .append("</code>.");
            }
            html.append("</td></tr>");
        }
        html.append("</tbody></table></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
