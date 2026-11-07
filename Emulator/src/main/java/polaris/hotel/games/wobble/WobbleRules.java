package polaris.hotel.games.wobble;

import com.google.gson.JsonObject;

/**
 * The rules of a duel, chosen by its host: the rounds to win (1, 2 or 3: a match in one, three or five falls), whether
 * a robot may take the other side of the beam, and how well the robot plays (1 easy, 2 normal, 3 hard).
 */
public record WobbleRules(int roundsToWin, boolean bots, int botLevel) {

    public static WobbleRules standard() {
        return new WobbleRules(2, true, 2);
    }

    /** The rules a host asked for, kept within bounds; what they did not send stays as it was. */
    public static WobbleRules from(JsonObject body, WobbleRules base) {
        if (body == null) return base;

        return new WobbleRules(
                clamp(intOf(body, "roundsToWin", base.roundsToWin()), 1, 3),
                boolOf(body, "bots", base.bots()),
                clamp(intOf(body, "botLevel", base.botLevel()), 1, 3));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("roundsToWin", this.roundsToWin);
        json.addProperty("bots", this.bots);
        json.addProperty("botLevel", this.botLevel);
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
