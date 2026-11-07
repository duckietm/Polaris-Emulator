package polaris.hotel.games.battleball;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rules of a BattleBall game, chosen by its host: the arena (1 Sky Peak, 2 Coral Beach, 3 Maze Park, 4 Gothic
 * Hallway, 5 Barebones Classic), the number of teams (2 to 4) and of players a team (1 to 3), the power-ups allowed
 * (1 to 8; none in Barebones Classic, as in the original), robots, and the length of a game in minutes.
 */
public record BattleBallRules(int arena, int teams, int perTeam, Set<Integer> powers, boolean bots, int minutes) {

    public static final int POWER_TYPES = 8;

    public BattleBallRules {
        Set<Integer> kept = new TreeSet<>();

        if (arena != 5) {
            for (Integer power : powers) {
                if (power != null && power >= 1 && power <= POWER_TYPES) kept.add(power);
            }
        }

        powers = Collections.unmodifiableSet(kept);
    }

    public static BattleBallRules standard() {
        return new BattleBallRules(1, 2, 3, Set.of(1, 2, 3, 4, 5, 6, 7, 8), true, 3);
    }

    public int maxPlayers() {
        return this.teams * this.perTeam;
    }

    /** The rules a host asked for, kept within bounds; what they did not send stays as it was. */
    public static BattleBallRules from(JsonObject body, BattleBallRules base) {
        if (body == null) return base;

        Set<Integer> powers = new TreeSet<>(base.powers());

        if (body.has("powers") && body.get("powers").isJsonArray()) {
            powers.clear();

            for (JsonElement element : body.getAsJsonArray("powers")) {
                try {
                    powers.add(element.getAsInt());
                } catch (RuntimeException e) {
                    // Not a power-up: left out.
                }
            }
        }

        return new BattleBallRules(
                clamp(intOf(body, "arena", base.arena()), 1, 5),
                clamp(intOf(body, "teams", base.teams()), 2, 4),
                clamp(intOf(body, "perTeam", base.perTeam()), 1, 3),
                powers,
                boolOf(body, "bots", base.bots()),
                clamp(intOf(body, "minutes", base.minutes()), 2, 5));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray powers = new JsonArray();

        for (int power : this.powers) powers.add(power);

        json.addProperty("arena", this.arena);
        json.addProperty("teams", this.teams);
        json.addProperty("perTeam", this.perTeam);
        json.add("powers", powers);
        json.addProperty("bots", this.bots);
        json.addProperty("minutes", this.minutes);
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
