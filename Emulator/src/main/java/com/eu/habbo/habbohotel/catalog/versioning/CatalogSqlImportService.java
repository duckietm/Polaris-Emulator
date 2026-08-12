package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CatalogSqlImportService {
    private final Gson gson = new Gson();
    private static final Pattern INSERT = Pattern.compile(
            "(?is)^INSERT\\s+INTO\\s+(catalog_pages|catalog_items)\\s*\\(([^)]*)\\)\\s*VALUES\\s*\\((.*)\\)$");
    private static final Pattern UPDATE =
            Pattern.compile("(?is)^UPDATE\\s+(catalog_pages|catalog_items)\\s+SET\\s+(.+)\\s+WHERE\\s+id\\s*=\\s*(\\d+)"
                    + "(?:\\s+AND\\s+catalog_type\\s*=\\s*('[^']*'|[A-Za-z_]+))?\\s*$");
    private static final Pattern DELETE =
            Pattern.compile("(?is)^DELETE\\s+FROM\\s+(catalog_pages|catalog_items)\\s+WHERE\\s+id\\s*=\\s*(\\d+)"
                    + "(?:\\s+AND\\s+catalog_type\\s*=\\s*('[^']*'|[A-Za-z_]+))?\\s*$");
    private static final Pattern DELETE_ALL =
            Pattern.compile("(?is)^DELETE\\s+FROM\\s+(catalog_pages|catalog_items)\\s*$");
    private static final Set<String> PAGE_COLUMNS = Set.of(
            "id",
            "catalog_type",
            "parent_id",
            "caption_save",
            "caption",
            "page_layout",
            "icon_color",
            "icon_image",
            "min_rank",
            "order_num",
            "visible",
            "enabled",
            "club_only",
            "catalog_mode",
            "vip_only",
            "page_headline",
            "page_teaser",
            "page_special",
            "page_text1",
            "page_text2",
            "page_text_details",
            "page_text_teaser",
            "room_id",
            "includes");
    private static final Set<String> OFFER_COLUMNS = Set.of(
            "id",
            "catalog_type",
            "item_ids",
            "page_id",
            "catalog_name",
            "cost_credits",
            "cost_points",
            "points_type",
            "amount",
            "limited_stack",
            "order_number",
            "offer_id",
            "song_id",
            "extradata",
            "have_offer",
            "club_only");

    public List<CatalogSqlStatement> parse(String sql) {
        List<CatalogSqlStatement> result = new ArrayList<>();
        for (String statement : new CatalogSqlTokenizer().statements(sql)) result.add(parseStatement(statement));
        if (result.isEmpty()) throw new IllegalArgumentException("SQL import contains no statements");
        return List.copyOf(result);
    }

    public CatalogImportDryRun dryRun(CatalogVersionSnapshot current, String sql) {
        List<CatalogPageSnapshot> pages = new ArrayList<>(current.pages());
        List<CatalogOfferSnapshot> offers = new ArrayList<>(current.offers());
        for (CatalogSqlStatement statement : parse(sql)) {
            if ("catalog_pages".equals(statement.table())) applyPage(pages, statement);
            else applyOffer(offers, statement);
        }
        CatalogVersionSnapshot target = new CatalogVersionSnapshot(current.version(), pages, offers);
        validateReferences(target);
        var changes = CatalogChangeSetSupport.diff(current, target, gson);
        return new CatalogImportDryRun(
                CatalogChangeSource.SQL,
                current.version().id(),
                current.version().revision(),
                new CatalogSqlExportService().export(target),
                changes,
                CatalogChangeSetSupport.fingerprint(
                        current.version().id(), current.version().revision(), changes, gson));
    }

    private void applyPage(List<CatalogPageSnapshot> pages, CatalogSqlStatement statement) {
        if (statement.action() == CatalogSqlAction.DELETE_ALL) {
            pages.clear();
            return;
        }
        int id = statement.action() == CatalogSqlAction.INSERT
                ? Integer.parseInt(statement.values().get("id"))
                : statement.whereId();
        CatalogPageType type = type(statement.values());
        int index = indexOfPage(pages, type, id);
        if (statement.action() == CatalogSqlAction.DELETE) {
            if (index < 0) throw new IllegalArgumentException("Catalog page not found: " + id);
            pages.remove(index);
            return;
        }
        JsonObject object =
                index < 0 ? new JsonObject() : gson.toJsonTree(pages.get(index)).getAsJsonObject();
        applyValues(object, statement.values(), true);
        CatalogPageSnapshot page = gson.fromJson(object, CatalogPageSnapshot.class);
        if (index < 0) pages.add(page);
        else pages.set(index, page);
    }

    private void applyOffer(List<CatalogOfferSnapshot> offers, CatalogSqlStatement statement) {
        if (statement.action() == CatalogSqlAction.DELETE_ALL) {
            offers.clear();
            return;
        }
        int id = statement.action() == CatalogSqlAction.INSERT
                ? Integer.parseInt(statement.values().get("id"))
                : statement.whereId();
        CatalogPageType type = type(statement.values());
        int index = indexOfOffer(offers, type, id);
        if (statement.action() == CatalogSqlAction.DELETE) {
            if (index < 0) throw new IllegalArgumentException("Catalog offer not found: " + id);
            offers.remove(index);
            return;
        }
        JsonObject object = index < 0
                ? new JsonObject()
                : gson.toJsonTree(offers.get(index)).getAsJsonObject();
        applyValues(object, statement.values(), false);
        CatalogOfferSnapshot offer = gson.fromJson(object, CatalogOfferSnapshot.class);
        if (index < 0) offers.add(offer);
        else offers.set(index, offer);
    }

    private static void applyValues(JsonObject object, Map<String, String> values, boolean page) {
        Set<String> numeric = page
                ? Set.of("id", "parent_id", "icon_color", "icon_image", "min_rank", "order_num", "room_id")
                : Set.of(
                        "id",
                        "page_id",
                        "cost_credits",
                        "cost_points",
                        "points_type",
                        "amount",
                        "limited_stack",
                        "order_number",
                        "offer_id",
                        "song_id");
        Set<String> bools =
                page ? Set.of("visible", "enabled", "club_only", "vip_only") : Set.of("have_offer", "club_only");
        values.forEach((column, value) -> {
            String property = property(column, page);
            if (value == null) object.add(property, JsonNull.INSTANCE);
            else if (numeric.contains(column)) object.add(property, new JsonPrimitive(Integer.parseInt(value)));
            else if (bools.contains(column)) object.add(property, new JsonPrimitive(!"0".equals(value)));
            else object.add(property, new JsonPrimitive(value));
        });
    }

    private static String property(String column, boolean page) {
        if ("id".equals(column)) return page ? "pageId" : "offerId";
        if ("catalog_type".equals(column)) return "catalogType";
        if (!page && "offer_id".equals(column)) return "offerIdClient";
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char c : column.toCharArray()) {
            if (c == '_') upper = true;
            else {
                result.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return result.toString();
    }

    private static CatalogPageType type(Map<String, String> values) {
        return CatalogPageType.fromString(values.getOrDefault("catalog_type", "NORMAL"));
    }

    private static int indexOfPage(List<CatalogPageSnapshot> pages, CatalogPageType type, int id) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).catalogType() == type && pages.get(index).pageId() == id) return index;
        }
        return -1;
    }

    private static int indexOfOffer(List<CatalogOfferSnapshot> offers, CatalogPageType type, int id) {
        for (int index = 0; index < offers.size(); index++) {
            if (offers.get(index).catalogType() == type && offers.get(index).offerId() == id) return index;
        }
        return -1;
    }

    private static void validateReferences(CatalogVersionSnapshot snapshot) {
        for (CatalogOfferSnapshot offer : snapshot.offers()) {
            if (snapshot.page(offer.catalogType(), offer.pageId()).isEmpty()) {
                throw new IllegalArgumentException("Offer references a missing page: " + offer.pageId());
            }
        }
    }

    private static CatalogSqlStatement parseStatement(String sql) {
        rejectDangerousSyntax(sql);
        Matcher insert = INSERT.matcher(sql);
        if (insert.matches()) {
            String table = insert.group(1).toLowerCase(Locale.ROOT);
            List<String> columns = splitComma(insert.group(2));
            List<String> values = splitComma(insert.group(3));
            if (columns.size() != values.size())
                throw new IllegalArgumentException("INSERT column/value count differs");
            Map<String, String> mapped = new LinkedHashMap<>();
            for (int index = 0; index < columns.size(); index++) {
                String column = identifier(columns.get(index));
                checkColumn(table, column);
                if (mapped.put(column, literal(values.get(index))) != null) {
                    throw new IllegalArgumentException("Duplicate SQL column: " + column);
                }
            }
            requireId(mapped);
            return new CatalogSqlStatement(CatalogSqlAction.INSERT, table, mapped, null);
        }

        Matcher update = UPDATE.matcher(sql);
        if (update.matches()) {
            String table = update.group(1).toLowerCase(Locale.ROOT);
            Map<String, String> mapped = new LinkedHashMap<>();
            for (String assignment : splitComma(update.group(2))) {
                int equals = indexOfEquals(assignment);
                if (equals <= 0) throw new IllegalArgumentException("Invalid SQL assignment");
                String column = identifier(assignment.substring(0, equals));
                checkColumn(table, column);
                if ("id".equals(column)) throw new IllegalArgumentException("Stable IDs cannot be updated");
                if (mapped.put(column, literal(assignment.substring(equals + 1))) != null) {
                    throw new IllegalArgumentException("Duplicate SQL column: " + column);
                }
            }
            if (update.group(4) != null) mapped.put("catalog_type", literal(update.group(4)));
            return new CatalogSqlStatement(CatalogSqlAction.UPDATE, table, mapped, Integer.valueOf(update.group(3)));
        }

        Matcher delete = DELETE.matcher(sql);
        if (delete.matches()) {
            Map<String, String> selector =
                    delete.group(3) == null ? Map.of() : Map.of("catalog_type", literal(delete.group(3)));
            return new CatalogSqlStatement(
                    CatalogSqlAction.DELETE,
                    delete.group(1).toLowerCase(Locale.ROOT),
                    selector,
                    Integer.valueOf(delete.group(2)));
        }
        Matcher deleteAll = DELETE_ALL.matcher(sql);
        if (deleteAll.matches()) {
            return new CatalogSqlStatement(
                    CatalogSqlAction.DELETE_ALL,
                    deleteAll.group(1).toLowerCase(Locale.ROOT),
                    Map.of(),
                    null);
        }
        throw new IllegalArgumentException("Only restricted INSERT, UPDATE and DELETE are supported");
    }

    private static void rejectDangerousSyntax(String sql) {
        String upper = outsideLiterals(sql).toUpperCase(Locale.ROOT);
        for (String token : List.of(
                "SELECT",
                " JOIN ",
                "CALL ",
                "PROCEDURE",
                "FUNCTION",
                "INTO OUTFILE",
                "LOAD_FILE",
                "BEGIN",
                "COMMIT",
                "ROLLBACK",
                "SAVEPOINT",
                "SET @",
                "@@",
                "--",
                "/*",
                "*/")) {
            if (upper.contains(token)) throw new IllegalArgumentException("Forbidden SQL construct: " + token.trim());
        }
    }

    private static String outsideLiterals(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean quoted = false;
        for (int index = 0; index < sql.length(); index++) {
            char c = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (quoted) {
                result.append(' ');
                if (c == '\\' && next != '\0') {
                    result.append(' ');
                    index++;
                } else if (c == '\'' && next == '\'') {
                    result.append(' ');
                    index++;
                } else if (c == '\'') quoted = false;
            } else {
                result.append(c == '\'' ? ' ' : c);
                if (c == '\'') quoted = true;
            }
        }
        return result.toString();
    }

    private static List<String> splitComma(String text) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            if (quoted) {
                current.append(c);
                if (c == '\'' && next == '\'') current.append(text.charAt(++index));
                else if (c == '\'') quoted = false;
            } else if (c == '\'') {
                quoted = true;
                current.append(c);
            } else if (c == ',') {
                values.add(current.toString().trim());
                current.setLength(0);
            } else current.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unterminated SQL literal");
        values.add(current.toString().trim());
        return values;
    }

    private static int indexOfEquals(String assignment) {
        boolean quoted = false;
        for (int index = 0; index < assignment.length(); index++) {
            char c = assignment.charAt(index);
            if (c == '\'') quoted = !quoted;
            else if (c == '=' && !quoted) return index;
        }
        return -1;
    }

    private static String identifier(String raw) {
        String value = raw.trim().replace("`", "").toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z_][a-z0-9_]*")) throw new IllegalArgumentException("Invalid SQL identifier");
        return value;
    }

    private static String literal(String raw) {
        String value = raw.trim();
        if (value.equalsIgnoreCase("NULL")) return null;
        if (value.matches("-?\\d+")) return value;
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        throw new IllegalArgumentException("Only integer, string and NULL SQL literals are supported");
    }

    private static void checkColumn(String table, String column) {
        Set<String> allowed = "catalog_pages".equals(table) ? PAGE_COLUMNS : OFFER_COLUMNS;
        if (!allowed.contains(column)) throw new IllegalArgumentException("Column is not allowed: " + column);
    }

    private static void requireId(Map<String, String> values) {
        String id = values.get("id");
        if (id == null || !id.matches("\\d+") || Integer.parseInt(id) <= 0) {
            throw new IllegalArgumentException("INSERT requires one positive stable ID");
        }
    }
}
