package polaris.hotel.games.fastfood;

import com.google.gson.JsonObject;

/**
 * The rules of a Fast Food game, chosen by its host: the number of tables (2 or 3, the original's three), the plates to
 * serve to win (3 to 6, the original's six stars), robots, and the power-ups (missile, shield, big parachute).
 */
public record FastFoodRules(int tables, int plates, boolean bots, boolean powers) {

    public static FastFoodRules standard() {
        return new FastFoodRules(3, 6, true, true);
    }

    /** The rules a host asked for, kept within bounds; what they did not send stays as it was. */
    public static FastFoodRules from(JsonObject body, FastFoodRules base) {
        if (body == null) return base;

        return new FastFoodRules(
                clamp(intOf(body, "tables", base.tables()), 2, 3),
                clamp(intOf(body, "plates", base.plates()), 3, 6),
                boolOf(body, "bots", base.bots()),
                boolOf(body, "powers", base.powers()));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("tables", this.tables);
        json.addProperty("plates", this.plates);
        json.addProperty("bots", this.bots);
        json.addProperty("powers", this.powers);
        return json;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int intOf(JsonObject body, String key, int fallback) {
        try {
            return body.has(key) ? body.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean boolOf(JsonObject body, String key, boolean fallback) {
        try {
            return body.has(key) ? body.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
