package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.networking.gameserver.wired.WiredApiAuth.Level;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.Scope;
import com.eu.habbo.networking.gameserver.wired.WiredApiRooms.TargetKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The variables web API under {@code /api/public}: one route table serves the requests and the
 * OpenAPI document. Each request passes, in order: enabled check, CORS preflight, client address
 * lockout and limit, body size, route and path validation, key check, per-key limit, the endpoint.
 */
final class WiredApiRouter {
    static final String PREFIX = "/api/public";
    static final String DOCS_PATH = PREFIX + "/api-docs";
    static final String ALLOWED_HEADERS = "Authorization, X-Api-Key, Content-Type";
    static final String EXPOSED_HEADERS = "Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset";

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredApiRouter.class);
    private static final Pattern POSITIVE_INT = Pattern.compile("[1-9][0-9]{0,9}");
    private static final int MAX_SEGMENTS = 12;
    private static final int MAX_SEGMENT_LENGTH = 64;

    @FunctionalInterface
    interface Handler {
        WiredApiResponse handle(WiredApiEndpoints.Call call);
    }

    /** One endpoint: what the docs show and what serves it. */
    record Route(
            String method,
            String path,
            Level level,
            Set<String> query,
            String summary,
            String requestSchema,
            String responseSchema,
            Handler handler) {

        List<String> segments() {
            return split(this.path);
        }

        int literals() {
            int count = 0;
            for (String segment : this.segments()) {
                if (!segment.startsWith("{")) {
                    count++;
                }
            }
            return count;
        }
    }

    static final List<Route> ROUTES = routes();

    private final Supplier<WiredApiSettings> settings;
    private final WiredApiLimits limits;
    private final WiredApiAuth auth;

    WiredApiRouter(Supplier<WiredApiSettings> settings, WiredApiRooms rooms, WiredApiLimits limits) {
        this.settings = settings;
        this.limits = limits;
        this.auth = new WiredApiAuth(rooms, limits);
    }

    static boolean handles(String path) {
        return path.equals(PREFIX) || path.startsWith(PREFIX + "/");
    }

    private static List<Route> routes() {
        String room = "/rooms/{roomId}";
        String entry = room + "/variables/{scope}/{variableName}/{targetKind}/{entityId}";
        String holders = room + "/variables/{scope}/{variableName}/{targetKind}";
        Set<String> none = Set.of();
        List<Route> routes = new ArrayList<>();
        routes.add(new Route(
                "GET",
                room + "/variables",
                Level.READ,
                none,
                "List the room's permanent variables.",
                null,
                "VariableList",
                WiredApiEndpoints::listVariables));
        routes.add(new Route(
                "GET",
                entry,
                Level.READ,
                none,
                "Read one holder's variable.",
                null,
                "Entry",
                WiredApiEndpoints::getEntry));
        routes.add(new Route(
                "PUT",
                entry,
                Level.WRITE,
                none,
                "Give the holder the variable, creating or replacing it.",
                "PutBody",
                "Entry",
                WiredApiEndpoints::putEntry));
        routes.add(new Route(
                "PATCH",
                entry,
                Level.WRITE,
                none,
                "Change the value of a variable the holder has.",
                "PatchBody",
                "Entry",
                WiredApiEndpoints::patchEntry));
        routes.add(new Route(
                "DELETE",
                entry,
                Level.WRITE,
                none,
                "Take the variable from the holder.",
                null,
                null,
                WiredApiEndpoints::deleteEntry));
        routes.add(new Route(
                "GET",
                holders,
                Level.READ,
                Set.of("page", "pageSize", "sort", "order"),
                "List the holders of a variable, paged.",
                null,
                "EntryPage",
                WiredApiEndpoints::listEntries));
        routes.add(new Route(
                "GET",
                holders + "/count",
                Level.READ,
                none,
                "Count the holders of a variable.",
                null,
                "Count",
                WiredApiEndpoints::countEntries));
        routes.add(new Route(
                "POST",
                room + "/variables/bulk-delete",
                Level.BULK,
                none,
                "Take variables from every holder (needs the bulk-delete permission).",
                "BulkDeleteBody",
                "BulkDeleteResult",
                WiredApiEndpoints::bulkDelete));
        routes.add(new Route(
                "POST",
                room + "/variables/{scope}/{variableName}/batch",
                Level.WRITE,
                none,
                "Apply several set, add or delete operations to one variable.",
                "BatchBody",
                "BatchResult",
                WiredApiEndpoints::batch));
        routes.add(new Route(
                "GET",
                room + "/variables/global/{variableName}",
                Level.READ,
                none,
                "Read a global variable.",
                null,
                "Value",
                WiredApiEndpoints::getGlobal));
        routes.add(new Route(
                "PATCH",
                room + "/variables/global/{variableName}",
                Level.WRITE,
                none,
                "Change a global variable.",
                "GlobalPatchBody",
                "Value",
                WiredApiEndpoints::patchGlobal));
        routes.add(new Route(
                "GET",
                room + "/variables_profile/user/users",
                Level.READ,
                Set.of("name", "unique_id"),
                "A user's permanent variables, found by name or user id.",
                null,
                "Profile",
                WiredApiEndpoints::userProfileByQuery));
        routes.add(new Route(
                "GET",
                room + "/variables_profile/user/{targetKind}/{entityId}",
                Level.READ,
                none,
                "A user's permanent variables.",
                null,
                "Profile",
                call -> WiredApiEndpoints.getProfile(call, Scope.USER)));
        routes.add(new Route(
                "PATCH",
                room + "/variables_profile/user/{targetKind}/{entityId}",
                Level.WRITE,
                none,
                "Set, create or delete several of a user's variables.",
                "ProfilePatchBody",
                "Profile",
                call -> WiredApiEndpoints.patchProfile(call, Scope.USER)));
        routes.add(new Route(
                "DELETE",
                room + "/variables_profile/user/{targetKind}/{entityId}",
                Level.WRITE,
                none,
                "Remove every permanent user variable of the holder.",
                null,
                null,
                WiredApiEndpoints::deleteUserProfile));
        routes.add(new Route(
                "GET",
                room + "/variables_profile/furni/{targetKind}/{entityId}",
                Level.READ,
                none,
                "A furni's permanent variables.",
                null,
                "Profile",
                call -> WiredApiEndpoints.getProfile(call, Scope.FURNI)));
        routes.add(new Route(
                "PATCH",
                room + "/variables_profile/furni/{targetKind}/{entityId}",
                Level.WRITE,
                none,
                "Set, create or delete several of a furni's variables.",
                "ProfilePatchBody",
                "Profile",
                call -> WiredApiEndpoints.patchProfile(call, Scope.FURNI)));
        routes.add(new Route(
                "GET",
                room + "/variables_profile/global",
                Level.READ,
                none,
                "Every permanent global variable of the room.",
                null,
                "Profile",
                WiredApiEndpoints::getGlobalProfile));
        routes.add(new Route(
                "PATCH",
                room + "/variables_profile/global",
                Level.WRITE,
                none,
                "Set several global variables.",
                "GlobalProfilePatchBody",
                "Profile",
                WiredApiEndpoints::patchGlobalProfile));
        routes.add(new Route(
                "GET", "/api-docs", Level.NONE, none, "This API as an OpenAPI 3 document.", null, null, null));
        routes.add(new Route("GET", "/api-docs/", Level.NONE, none, "This API as a page.", null, null, null));
        return List.copyOf(routes);
    }

    WiredApiResponse handle(WiredApiRequest request) {
        WiredApiSettings current = this.settings.get();
        if (!current.enabled()) {
            return this.error(request, current, new WiredApiException(404, "disabled", "Not found."), null);
        }
        if (request.method().equals("OPTIONS")) {
            return this.preflight(request, current);
        }

        WiredApiLimits.Window window = null;
        Route route = null;
        try {
            this.limits.checkNotBlocked(request.clientIp());
            window = this.limits.acquireIp(request.clientIp(), current);
            if (request.body() != null && request.body().length > current.maxPayloadBytes()) {
                throw new WiredApiException(413, "payload_too_large", "The body is too large.");
            }

            List<String> segments = pathSegments(request.path());
            route = this.match(request.method(), segments);
            if (route.level() == Level.NONE) {
                return this.finish(request, current, docs(route, request), window);
            }

            Map<String, String> params = params(route, segments);
            WiredApiAuth.refuseKeyInQuery(request);
            for (String name : request.query().keySet()) {
                if (!route.query().contains(name)) {
                    throw WiredApiException.badRequest("Unknown query parameter '" + WiredApiJson.safe(name) + "'.");
                }
            }
            for (List<String> values : request.query().values()) {
                if (values.size() > 1) {
                    throw WiredApiException.badRequest("Query parameters may appear once.");
                }
            }
            checkBody(request);

            int roomId = Integer.parseInt(params.get("roomId"));
            WiredApiAuth.Session session = this.auth.authenticate(request, roomId, route.level(), current);
            if (session.window() != null) {
                window = session.window();
            }
            WiredApiResponse response =
                    route.handler().handle(new WiredApiEndpoints.Call(request, params, session, current, this.limits));
            return this.finish(request, current, response, window);
        } catch (WiredApiException e) {
            return this.error(request, current, e, window);
        } catch (RuntimeException e) {
            LOGGER.error(
                    "Variables web API request failed: {} {}",
                    request.method(),
                    route == null ? "(unrouted)" : route.path(),
                    e);
            return this.error(request, current, new WiredApiException(500, "internal", "Internal error."), window);
        }
    }

    private Route match(String method, List<String> segments) {
        List<Route> candidates = new ArrayList<>();
        int best = -1;
        for (Route route : ROUTES) {
            if (!shapeMatches(route.segments(), segments)) {
                continue;
            }
            int literals = route.literals();
            if (literals > best) {
                candidates.clear();
                best = literals;
            }
            if (literals == best) {
                candidates.add(route);
            }
        }
        if (candidates.isEmpty()) {
            throw WiredApiException.notFound("Unknown endpoint.");
        }
        for (Route route : candidates) {
            if (route.method().equals(method)) {
                return route;
            }
        }
        Set<String> allowed = new LinkedHashSet<>();
        candidates.forEach(route -> allowed.add(route.method()));
        throw new WiredApiException(405, "method_not_allowed", "Allowed: " + String.join(", ", allowed) + ".");
    }

    private static boolean shapeMatches(List<String> template, List<String> segments) {
        if (template.size() != segments.size()) {
            return false;
        }
        for (int i = 0; i < template.size(); i++) {
            String expected = template.get(i);
            if (!expected.startsWith("{") && !expected.equals(segments.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** The path below the prefix as segments; a trailing slash is kept as an empty last segment. */
    static List<String> pathSegments(String path) {
        String rest = path.length() > PREFIX.length() ? path.substring(PREFIX.length()) : "";
        List<String> segments = split(rest);
        if (segments.size() > MAX_SEGMENTS) {
            throw WiredApiException.notFound("Unknown endpoint.");
        }
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            boolean trailing = i == segments.size() - 1 && segment.isEmpty();
            if ((segment.isEmpty() && !trailing) || segment.length() > MAX_SEGMENT_LENGTH) {
                throw WiredApiException.notFound("Unknown endpoint.");
            }
        }
        return segments;
    }

    private static List<String> split(String path) {
        List<String> segments = new ArrayList<>();
        if (path.isEmpty() || path.equals("/")) {
            return path.equals("/") ? List.of("") : segments;
        }
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        for (String segment : trimmed.split("/", -1)) {
            segments.add(segment);
        }
        return segments;
    }

    private static Map<String, String> params(Route route, List<String> segments) {
        Map<String, String> params = new LinkedHashMap<>();
        List<String> template = route.segments();
        for (int i = 0; i < template.size(); i++) {
            String name = template.get(i);
            if (name.startsWith("{")) {
                params.put(name.substring(1, name.length() - 1), segments.get(i));
            }
        }
        positiveInt(params.get("roomId"), "roomId");
        if (params.containsKey("entityId")) {
            positiveInt(params.get("entityId"), "entityId");
        }
        if (params.containsKey("variableName")
                && !HotelWiredApiRooms.NAME.matcher(params.get("variableName")).matches()) {
            throw WiredApiException.badRequest("Invalid variable name.");
        }
        Scope scope = Scope.USER;
        if (params.containsKey("scope")) {
            scope = Scope.fromPath(params.get("scope"));
            if (scope == null || scope == Scope.GLOBAL) {
                throw WiredApiException.badRequest("'scope' must be user or furni.");
            }
        } else if (route.path().contains("/variables_profile/furni/")) {
            scope = Scope.FURNI;
        }
        if (params.containsKey("targetKind")) {
            TargetKind kind = TargetKind.fromPath(params.get("targetKind"));
            if (kind == null || kind.scope() != scope) {
                throw WiredApiException.badRequest(
                        scope == Scope.USER
                                ? "'targetKind' must be users, pets or bots."
                                : "'targetKind' must be floor or wall.");
            }
            if (kind == TargetKind.PETS || kind == TargetKind.BOTS) {
                throw WiredApiEndpoints.unsupportedHolders();
            }
        }
        return params;
    }

    static int positiveInt(String raw, String name) {
        if (raw == null || !POSITIVE_INT.matcher(raw).matches() || Long.parseLong(raw) > Integer.MAX_VALUE) {
            throw WiredApiException.badRequest("'" + name + "' must be a positive 32-bit integer.");
        }
        return Integer.parseInt(raw);
    }

    private static void checkBody(WiredApiRequest request) {
        boolean hasBody = request.body() != null && request.body().length > 0;
        boolean takesBody = Set.of("PUT", "PATCH", "POST").contains(request.method());
        if (hasBody && !takesBody) {
            throw WiredApiException.badRequest("This endpoint takes no body.");
        }
        if (takesBody) {
            String type = request.header("content-type");
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
                throw WiredApiException.badRequest("Send the body as application/json.");
            }
        }
    }

    private static WiredApiResponse docs(Route route, WiredApiRequest request) {
        if (!request.query().isEmpty()) {
            throw WiredApiException.badRequest("This endpoint takes no query parameters.");
        }
        if (route.path().endsWith("/")) {
            return new WiredApiResponse(200, "text/html; charset=utf-8", WiredApiOpenApi.html(ROUTES))
                    .header(
                            "Content-Security-Policy",
                            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'");
        }
        return WiredApiResponse.json(200, WiredApiOpenApi.document(ROUTES).toString());
    }

    private WiredApiResponse preflight(WiredApiRequest request, WiredApiSettings current) {
        WiredApiResponse response = WiredApiResponse.empty(204);
        String origin = current.allowedOrigin(request.header("origin"));
        if (origin != null) {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Allow-Methods", "GET, PUT, PATCH, DELETE, POST, OPTIONS");
            response.header("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            response.header("Access-Control-Max-Age", "600");
        }
        if (!"*".equals(origin)) {
            response.header("Vary", "Origin");
        }
        return common(response);
    }

    private WiredApiResponse error(
            WiredApiRequest request, WiredApiSettings current, WiredApiException e, WiredApiLimits.Window window) {
        WiredApiResponse response = WiredApiResponse.json(e.status(), WiredApiJson.error(e.code(), e.getMessage()));
        if (e.status() == 429) {
            response.header("Retry-After", Long.toString(e.retryAfterSeconds()));
            if (e.limit() > 0) {
                response.header("X-RateLimit-Limit", Integer.toString(e.limit()));
            }
            response.header("X-RateLimit-Remaining", "0");
            response.header("X-RateLimit-Reset", Long.toString(e.retryAfterSeconds()));
            window = null;
        }
        if (e.status() == 401) {
            response.header("WWW-Authenticate", "Bearer");
        }
        return this.finish(request, current, response, window);
    }

    private WiredApiResponse finish(
            WiredApiRequest request,
            WiredApiSettings current,
            WiredApiResponse response,
            WiredApiLimits.Window window) {
        if (window != null) {
            response.header("X-RateLimit-Limit", Integer.toString(window.limit()));
            response.header("X-RateLimit-Remaining", Integer.toString(window.remaining()));
            response.header("X-RateLimit-Reset", Long.toString(window.resetSeconds()));
        }
        String origin = current.allowedOrigin(request.header("origin"));
        if (origin != null) {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Expose-Headers", EXPOSED_HEADERS);
        }
        if (!"*".equals(origin)) {
            response.header("Vary", "Origin");
        }
        return common(response);
    }

    private static WiredApiResponse common(WiredApiResponse response) {
        response.header("Cache-Control", "no-store");
        response.header("X-Content-Type-Options", "nosniff");
        response.header("Referrer-Policy", "no-referrer");
        return response;
    }
}
