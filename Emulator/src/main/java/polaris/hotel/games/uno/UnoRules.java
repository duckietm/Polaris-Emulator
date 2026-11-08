package polaris.hotel.games.uno;

import com.google.gson.JsonObject;

/**
 * The rules a table plays with, chosen by its host before the game: the house rules players know (stacking the +2 and
 * the +4, jumping in with the same card, no finishing on a special card, drawing until a card fits), the robots, the
 * counter-UNO, the time per turn and the cards dealt.
 */
public record UnoRules(
        int maxPlayers,
        int handSize,
        int turnSeconds,
        boolean stackTwo,
        boolean stackFour,
        boolean jumpIn,
        boolean noSpecialFinish,
        boolean drawUntilPlayable,
        boolean unoPenalty,
        boolean bots) {

    public static final int MAX_CHAIRS = 8;

    public static UnoRules standard(int maxPlayers, int turnSeconds) {
        return new UnoRules(
                clamp(maxPlayers, 2, 6), 7, clamp(turnSeconds, 10, 60), false, false, false, false, false, true, true);
    }

    /** The rules a host asked for, kept within bounds; what they did not send stays as it was. */
    public static UnoRules from(JsonObject body, UnoRules base, int playersLimit) {
        if (body == null) return base;

        return new UnoRules(
                clamp(intOf(body, "maxPlayers", base.maxPlayers()), 2, Math.max(2, Math.min(6, playersLimit))),
                pick(intOf(body, "handSize", base.handSize()), 5, 7, 10),
                pick(intOf(body, "turnSeconds", base.turnSeconds()), 15, 25, 40),
                boolOf(body, "stackTwo", base.stackTwo()),
                boolOf(body, "stackFour", base.stackFour()),
                boolOf(body, "jumpIn", base.jumpIn()),
                boolOf(body, "noSpecialFinish", base.noSpecialFinish()),
                boolOf(body, "drawUntilPlayable", base.drawUntilPlayable()),
                boolOf(body, "unoPenalty", base.unoPenalty()),
                boolOf(body, "bots", base.bots()));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("maxPlayers", this.maxPlayers);
        json.addProperty("handSize", this.handSize);
        json.addProperty("turnSeconds", this.turnSeconds);
        json.addProperty("stackTwo", this.stackTwo);
        json.addProperty("stackFour", this.stackFour);
        json.addProperty("jumpIn", this.jumpIn);
        json.addProperty("noSpecialFinish", this.noSpecialFinish);
        json.addProperty("drawUntilPlayable", this.drawUntilPlayable);
        json.addProperty("unoPenalty", this.unoPenalty);
        json.addProperty("bots", this.bots);
        return json;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** One of the allowed values: the nearest one. */
    private static int pick(int value, int... allowed) {
        int best = allowed[0];

        for (int option : allowed) {
            if (Math.abs(option - value) < Math.abs(best - value)) best = option;
        }

        return best;
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
