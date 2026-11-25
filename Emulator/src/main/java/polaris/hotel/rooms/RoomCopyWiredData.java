package polaris.hotel.rooms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import java.util.Map;

/**
 * Re-points a wired box's saved data at the furni of a copied room. A box names the furni it works on (and the
 * variables, chests and contracts it uses, which are furni too) by item id; a copy's furni have new ids, so the
 * ids in the data are replaced by those of the copies.
 *
 * <p>Only numbers under a field that names a furni are touched (item, furni, chest, contract, variable, picked,
 * selected, secondary, target and audience ids), and only when the number is the id of a furni that was copied:
 * a base item id, a hand item, a colour or a delay stays what it is.
 */
public final class RoomCopyWiredData {
    private static final String[] FURNI_FIELDS = {
        "item", "furni", "chest", "contract", "variable", "picked", "selected", "secondary", "target", "audience"
    };
    private static final String[] OTHER_IDS = {"base", "hand", "sprite", "inventory", "cached", "group", "type_id"};

    private RoomCopyWiredData() {}

    /** The data with every furni id replaced; the data itself when it is not JSON or names none of the furni. */
    public static String remap(String wiredData, Map<Integer, Integer> newIdByOldId) {
        if (wiredData == null || newIdByOldId == null || newIdByOldId.isEmpty()) return wiredData;

        String trimmed = wiredData.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return wiredData;

        JsonElement root;
        try {
            root = JsonParser.parseString(trimmed);
        } catch (RuntimeException notJson) {
            return wiredData;
        }

        boolean[] changed = {false};
        JsonElement remapped = walk(root, false, newIdByOldId, changed);
        return changed[0] ? remapped.toString() : wiredData;
    }

    /** Whether a field of this name holds furni ids. */
    static boolean namesFurni(String field) {
        String name = field.toLowerCase(Locale.ROOT);
        if (!name.endsWith("id") && !name.endsWith("ids")) return false;
        if (name.equals("typeid")) return false;

        for (String other : OTHER_IDS) {
            if (name.contains(other)) return false;
        }
        for (String furni : FURNI_FIELDS) {
            if (name.contains(furni)) return true;
        }
        return false;
    }

    private static JsonElement walk(
            JsonElement element, boolean furniField, Map<Integer, Integer> ids, boolean[] changed) {
        if (element.isJsonObject()) {
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry :
                    element.getAsJsonObject().entrySet()) {
                copy.add(entry.getKey(), walk(entry.getValue(), namesFurni(entry.getKey()), ids, changed));
            }
            return copy;
        }

        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            // The entries of a list of ids are ids; the objects of a list are looked at field by field.
            for (JsonElement entry : element.getAsJsonArray()) copy.add(walk(entry, furniField, ids, changed));
            return copy;
        }

        if (furniField && element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                double value = primitive.getAsDouble();
                if (value == Math.rint(value) && value > 0 && value <= Integer.MAX_VALUE) {
                    Integer replacement = ids.get((int) value);
                    if (replacement != null) {
                        changed[0] = true;
                        return new JsonPrimitive(replacement);
                    }
                }
            }
        }
        return element;
    }
}
