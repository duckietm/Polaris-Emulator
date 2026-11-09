package polaris.hotel.catalog.versioning;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether the pages and offers an operation changed still hold what it left them with. The history only records
 * changes made through Catalog Studio; a page or offer edited another way (the All-in-1 Manager, the SQL editor, a
 * script) since then would be put back to its old value by an undo, losing that edit without a word. So an undo first
 * compares the live rows with the operation's last "after" of each entity.
 */
final class CatalogUndoDrift {
    private static final Gson GSON = new Gson();

    private CatalogUndoDrift() {}

    /** The first entity that differs from what the operation left, as "PAGE #12", or empty when all still match. */
    static Optional<String> firstDrifted(CatalogVersionSnapshot live, List<CatalogChangeEntry> entries) {
        Map<String, CatalogChangeEntry> last = new LinkedHashMap<>();
        for (CatalogChangeEntry entry : entries) {
            last.put(entry.entityType() + ":" + entry.catalogType() + ":" + entry.entityId(), entry);
        }
        for (CatalogChangeEntry entry : last.values()) {
            Optional<?> current = entry.entityType() == CatalogEntityType.PAGE
                    ? live.page(entry.catalogType(), entry.entityId())
                    : live.offer(entry.catalogType(), entry.entityId());
            if (!matches(entry.afterJson(), current.map(GSON::toJsonTree).orElse(null))) {
                return Optional.of(entry.entityType() + " #" + entry.entityId());
            }
        }
        return Optional.empty();
    }

    /** The live row matches the operation's after state: gone after a delete, the same fields otherwise. */
    static boolean matches(String afterJson, JsonElement current) {
        if (afterJson == null || afterJson.isBlank()) return current == null;
        if (current == null || !current.isJsonObject()) return false;
        JsonObject after = JsonParser.parseString(afterJson).getAsJsonObject();
        JsonObject now = current.getAsJsonObject();
        for (Map.Entry<String, JsonElement> field : after.entrySet()) {
            if (!same(field.getValue(), now.get(field.getKey()))) return false;
        }
        return true;
    }

    /** Equal values; a missing value, null and "" count as the same (TEXT columns read back as NULL or ""). */
    private static boolean same(JsonElement a, JsonElement b) {
        a = blankToNull(a);
        b = blankToNull(b);
        if (a == null || b == null) return a == b;
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive x = a.getAsJsonPrimitive();
            JsonPrimitive y = b.getAsJsonPrimitive();
            if (x.isNumber() && y.isNumber()) return x.getAsBigDecimal().compareTo(y.getAsBigDecimal()) == 0;
            return x.getAsString().equals(y.getAsString());
        }
        return a.equals(b);
    }

    private static JsonElement blankToNull(JsonElement value) {
        if (value == null || value instanceof JsonNull) return null;
        if (value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                && value.getAsString().isEmpty()) {
            return null;
        }
        return value;
    }
}
