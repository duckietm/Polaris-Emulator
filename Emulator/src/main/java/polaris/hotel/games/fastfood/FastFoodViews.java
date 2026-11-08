package polaris.hotel.games.fastfood;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** A Fast Food game as JSON: in the list of games, and for one of its players. */
public final class FastFoodViews {

    private FastFoodViews() {}

    public static JsonObject summary(FastFoodMatch match) {
        JsonObject summary = new JsonObject();
        FastFoodMatch.Player host = match.player(match.hostId());

        summary.addProperty("id", match.id());
        summary.addProperty("host", host == null ? "" : host.name);
        summary.addProperty("players", match.players().size());
        summary.addProperty("maxPlayers", match.rules().tables());
        summary.addProperty("state", match.state().name().toLowerCase());
        summary.add("rules", match.rules().toJson());
        return summary;
    }

    /** How the foods fall, for the client to move the plates between two answers as the game does. */
    public static JsonObject physics() {
        JsonObject physics = new JsonObject();
        JsonArray foods = new JsonArray();

        for (FastFoodMatch.Food food : FastFoodMatch.FOODS) {
            JsonObject entry = new JsonObject();

            entry.addProperty("name", food.name());
            entry.addProperty("gravity", food.gravity());
            entry.addProperty("brake", food.brake());
            entry.addProperty("terminal", food.terminal());
            foods.add(entry);
        }

        physics.add("foods", foods);
        physics.addProperty("safeSpeed", FastFoodMatch.SAFE_SPEED);
        physics.addProperty("bigBrake", FastFoodMatch.BIG_BRAKE);
        physics.addProperty("bigTerminal", FastFoodMatch.BIG_TERMINAL);
        physics.addProperty("stepMs", FastFoodMatch.STEP_MS);
        physics.addProperty("plateTimerMs", FastFoodMatch.PLATE_TIMER_MS);
        physics.addProperty("falseStartPenaltyMs", FastFoodMatch.FALSE_START_PENALTY_MS);
        return physics;
    }

    public static JsonObject match(FastFoodMatch match, int userId, long now) {
        JsonObject view = summary(match);
        FastFoodMatch.State state = match.state();

        view.addProperty("hostId", match.hostId());
        view.addProperty("youAreHost", match.hostId() == userId);
        view.addProperty(
                "timeLeft",
                state == FastFoodMatch.State.COUNTDOWN || state == FastFoodMatch.State.PLAYING
                        ? Math.max(0L, match.phaseEndsAt() - now)
                        : 0L);
        view.addProperty("serverTime", now);

        JsonArray winners = new JsonArray();

        for (int winner : match.winners()) winners.add(winner);

        view.add("winners", winners);

        JsonArray players = new JsonArray();

        for (FastFoodMatch.Player player : match.players()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("userId", player.userId);
            entry.addProperty("name", player.name);
            entry.addProperty("figure", player.figure);
            entry.addProperty("gender", player.gender);
            entry.addProperty("bot", player.bot);
            entry.addProperty("you", player.userId == userId);
            entry.addProperty("seat", player.seat);
            entry.addProperty("stars", player.stars);
            entry.addProperty("held", player.held);

            FastFoodMatch.Plate plate = player.plate;

            if (plate != null) {
                JsonObject p = new JsonObject();

                p.addProperty("id", plate.id);
                p.addProperty("food", plate.food);
                p.addProperty("phase", plate.phase.name().toLowerCase());
                p.addProperty("y", plate.y);
                p.addProperty("v", plate.v);
                // How far the fall was simulated (the client moves the plate on from there).
                p.addProperty("ago", plate.simAt < 0 ? 0L : Math.max(0L, now - plate.simAt));
                p.addProperty("elapsed", plate.releasedAt < 0 ? -1L : now - plate.releasedAt);
                p.addProperty("big", plate.big);
                p.addProperty("shield", plate.shield);
                p.addProperty("falseStart", plate.falseStart);
                p.addProperty("touched", plate.impactAt >= 0);
                p.addProperty("speed", plate.impactAt >= 0 ? plate.impactSpeed : plate.v);
                p.addProperty("readyIn", Math.max(0L, plate.readyAt - now));
                p.addProperty("endedAgo", plate.endedAt < 0 ? -1L : now - plate.endedAt);
                entry.add("plate", p);
            }

            players.add(entry);
        }

        view.add("players", players);

        JsonArray missiles = new JsonArray();

        for (long[] missile : match.missilesInAir(now)) {
            JsonObject entry = new JsonObject();

            entry.addProperty("from", missile[0]);
            entry.addProperty("to", missile[1]);
            entry.addProperty("hitIn", missile[2]);
            missiles.add(entry);
        }

        view.add("missiles", missiles);

        JsonArray log = new JsonArray();

        for (FastFoodMatch.Event event : match.log()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("seq", event.seq());
            entry.addProperty("ago", Math.max(0L, now - event.at()));
            entry.addProperty("type", event.type());
            entry.addProperty("userId", event.userId());
            entry.addProperty("a", event.a());
            entry.addProperty("b", event.b());
            entry.addProperty("c", event.c());
            log.add(entry);
        }

        view.add("log", log);
        return view;
    }
}
