package polaris.hotel.games.wobble;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** A duel as JSON: in the list of duels, and for one of its fighters (or somebody watching). */
public final class WobbleViews {

    private WobbleViews() {}

    public static JsonObject summary(WobbleMatch match) {
        JsonObject summary = new JsonObject();
        WobbleMatch.Fighter left = match.onSide(0);
        WobbleMatch.Fighter right = match.onSide(1);
        WobbleMatch.Fighter host = null;

        for (WobbleMatch.Fighter fighter : match.fighters()) {
            if (fighter.userId == match.hostId()) host = fighter;
        }

        summary.addProperty("id", match.id());
        summary.addProperty("host", host == null ? "" : host.name);
        summary.addProperty("left", left == null ? "" : left.name);
        summary.addProperty("right", right == null ? "" : right.name);
        summary.addProperty("players", match.fighters().size());
        summary.addProperty("state", match.state().name().toLowerCase());
        summary.add("rules", match.rules().toJson());
        return summary;
    }

    public static JsonObject match(WobbleMatch match, int userId, long now) {
        JsonObject view = summary(match);
        WobbleMatch.State state = match.state();
        long phaseLeft = state == WobbleMatch.State.COUNTDOWN || state == WobbleMatch.State.ROUND_OVER
                ? Math.max(0L, match.phaseEndsAt() - now)
                : 0L;

        view.addProperty("hostId", match.hostId());
        view.addProperty("youAreHost", match.hostId() == userId);
        view.addProperty("you", match.side(userId));
        view.addProperty("round", match.round());
        view.addProperty("phaseLeft", phaseLeft);
        view.addProperty(
                "roundTime", state == WobbleMatch.State.PLAYING ? Math.max(0L, now - match.roundStartedAt()) : 0L);
        view.addProperty("suddenIn", WobbleMatch.SUDDEN_MS);
        view.addProperty("sudden", match.sudden());
        view.addProperty("winnerSide", match.winnerSide());
        view.addProperty("serverTime", now);

        JsonArray fighters = new JsonArray();

        for (int side = 0; side < 2; side++) {
            WobbleMatch.Fighter fighter = match.onSide(side);

            if (fighter == null) continue;

            JsonObject entry = new JsonObject();

            entry.addProperty("side", side);
            entry.addProperty("userId", fighter.userId);
            entry.addProperty("name", fighter.name);
            entry.addProperty("figure", fighter.figure);
            entry.addProperty("gender", fighter.gender);
            entry.addProperty("bot", fighter.bot);
            entry.addProperty("you", fighter.userId == userId);
            entry.addProperty("pos", fighter.pos);
            entry.addProperty("balance", Math.round(fighter.balance * 10) / 10.0);
            entry.addProperty("speed", Math.round(fighter.speed * 100) / 100.0);
            entry.addProperty("lean", fighter.lean);
            entry.addProperty("stabilising", fighter.stabilising(now));
            entry.addProperty("pushIn", Math.max(0L, fighter.pushReadyAt - now));
            entry.addProperty("stabilised", fighter.stabilised);
            entry.addProperty("action", fighter.action);
            entry.addProperty("actionDir", fighter.actionDir);
            entry.addProperty("actionAgo", fighter.action.isEmpty() ? -1L : Math.max(0L, now - fighter.actionAt));
            entry.addProperty("fell", fighter.fell);
            entry.addProperty("wins", fighter.wins);
            fighters.add(entry);
        }

        view.add("fighters", fighters);

        JsonArray log = new JsonArray();

        for (WobbleMatch.Event event : match.log()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("seq", event.seq());
            entry.addProperty("type", event.type());
            entry.addProperty("a", event.a());
            entry.addProperty("b", event.b());
            log.add(entry);
        }

        view.add("log", log);
        return view;
    }
}
