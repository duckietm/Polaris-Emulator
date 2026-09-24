package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.networking.gameserver.wired.WiredApiAuth.Level;
import com.eu.habbo.networking.gameserver.wired.WiredApiRouter.Route;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The OpenAPI 3 document and a plain HTML page of it, both built from the route table. */
final class WiredApiOpenApi {
    private static final String NAME_PATTERN = "^[A-Za-z0-9_]{1,40}$";
    private static final List<String[]> ERRORS = List.of(
            new String[] {"400", "Invalid request (bad_request)."},
            new String[] {"401", "Missing or unknown key (unauthorized)."},
            new String[] {"403", "Key not allowed here (forbidden)."},
            new String[] {"404", "Unknown room, variable or holder (not_found)."},
            new String[] {"413", "Body too large (payload_too_large)."},
            new String[] {"429", "Rate limited (rate_limited); see Retry-After."});

    private WiredApiOpenApi() {}

    static JsonObject document(List<Route> routes) {
        JsonObject root = new JsonObject();
        root.addProperty("openapi", "3.0.3");
        JsonObject info = new JsonObject();
        info.addProperty("title", "Variables Web API");
        info.addProperty("version", "1");
        info.addProperty(
                "description",
                "Read and write a room's permanent wired variables. Keys come from the room's Variables Web API"
                        + " box and go in the Authorization header, never in the URL.");
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
                switch (route.level()) {
                    case READ -> "Read key or write key.";
                    case WRITE -> "Write key.";
                    case BULK -> "Write key, and the box must allow bulk delete.";
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
            case "targetKind" ->
                schema.add(
                        "enum", path.contains("furni") ? strings("floor", "wall") : strings("users", "pets", "bots"));
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
            case "page", "pageSize", "unique_id" -> {
                schema.addProperty("type", "integer");
                schema.addProperty("minimum", 1);
            }
            case "sort" -> {
                schema.addProperty("type", "string");
                schema.add("enum", strings("entityId", "value"));
            }
            case "order" -> {
                schema.addProperty("type", "string");
                schema.add("enum", strings("asc", "desc"));
            }
            default -> schema.addProperty("type", "string");
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
        schemas.add(
                "Error",
                object(
                        "error",
                        object(
                                "code",
                                enumString(
                                        "bad_request",
                                        "unauthorized",
                                        "forbidden",
                                        "not_found",
                                        "method_not_allowed",
                                        "conflict",
                                        "payload_too_large",
                                        "rate_limited",
                                        "disabled",
                                        "internal"),
                                "message",
                                type("string"))));
        schemas.add(
                "Variable",
                object(
                        "name",
                        type("string"),
                        "scope",
                        enumString("user", "furni", "global"),
                        "hasValue",
                        type("boolean"),
                        "textConnected",
                        type("boolean")));
        schemas.add("VariableList", object("variables", arrayOf("Variable")));
        schemas.add(
                "Entry",
                object(
                        "entityId",
                        type("integer"),
                        "value",
                        type("integer"),
                        "createdAt",
                        type("integer"),
                        "updatedAt",
                        type("integer")));
        schemas.add(
                "Value", object("value", type("integer"), "createdAt", type("integer"), "updatedAt", type("integer")));
        schemas.add(
                "EntryPage",
                object(
                        "page",
                        type("integer"),
                        "pageSize",
                        type("integer"),
                        "total",
                        type("integer"),
                        "entries",
                        arrayOf("Entry")));
        schemas.add("Count", object("count", type("integer")));
        schemas.add("PutBody", object("value", type("integer")));
        schemas.add("PatchBody", object("value", type("integer"), "add", type("integer")));
        schemas.add("GlobalPatchBody", object("value", type("integer"), "add", type("integer")));
        JsonObject names = new JsonObject();
        names.addProperty("type", "array");
        names.add("items", type("string"));
        schemas.add("BulkDeleteBody", object("names", names));
        JsonObject counts = new JsonObject();
        counts.addProperty("type", "object");
        counts.add("additionalProperties", type("integer"));
        schemas.add("BulkDeleteResult", object("deleted", counts));
        schemas.add(
                "Operation",
                object(
                        "op",
                        enumString("set", "add", "delete"),
                        "targetKind",
                        enumString("users", "floor", "wall"),
                        "entityId",
                        type("integer"),
                        "value",
                        type("integer")));
        schemas.add("BatchBody", object("operations", arrayOf("Operation")));
        JsonObject result = object("ok", type("boolean"), "entry", ref("Entry"), "error", ref("Error"));
        schemas.add("BatchResult", object("results", arrayOfSchema(result)));
        JsonObject values = new JsonObject();
        values.addProperty("type", "object");
        values.addProperty("description", "Variable name to an integer, null (delete) or true (create without value).");
        schemas.add("ProfilePatchBody", object("variables", values));
        JsonObject globals = new JsonObject();
        globals.addProperty("type", "object");
        globals.add("additionalProperties", type("integer"));
        schemas.add("GlobalProfilePatchBody", object("variables", globals));
        JsonObject profileValues = new JsonObject();
        profileValues.addProperty("type", "object");
        profileValues.add("additionalProperties", ref("Value"));
        schemas.add(
                "Profile",
                object(
                        "targetKind",
                        type("string"),
                        "entityId",
                        type("integer"),
                        "name",
                        type("string"),
                        "variables",
                        profileValues));

        JsonObject securitySchemes = new JsonObject();
        JsonObject bearer = new JsonObject();
        bearer.addProperty("type", "http");
        bearer.addProperty("scheme", "bearer");
        securitySchemes.add("bearerKey", bearer);
        JsonObject header = new JsonObject();
        header.addProperty("type", "apiKey");
        header.addProperty("in", "header");
        header.addProperty("name", "X-Api-Key");
        securitySchemes.add("apiKeyHeader", header);

        JsonObject components = new JsonObject();
        components.add("schemas", schemas);
        components.add("securitySchemes", securitySchemes);
        return components;
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
                .append("</code>. Send the key as <code>Authorization: Bearer &lt;key&gt;</code> ")
                .append("(or <code>X-Api-Key</code>); keys in the URL are refused. ")
                .append("Errors look like <code>{\"error\":{\"code\",\"message\"}}</code>. ")
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
