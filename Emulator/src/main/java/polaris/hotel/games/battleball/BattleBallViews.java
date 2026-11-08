package polaris.hotel.games.battleball;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** A BattleBall game as JSON: in the list of games, and for one of its players. */
public final class BattleBallViews {

    private BattleBallViews() {}

    public static JsonObject summary(BattleBallMatch match) {
        JsonObject summary = new JsonObject();
        BattleBallMatch.Player host = match.player(match.hostId());
        JsonArray teams = new JsonArray();

        for (int team = 0; team < match.rules().teams(); team++) teams.add(match.teamSize(team));

        summary.addProperty("id", match.id());
        summary.addProperty("host", host == null ? "" : host.name);
        summary.addProperty("players", match.players().size());
        summary.addProperty("maxPlayers", match.rules().maxPlayers());
        summary.add("teams", teams);
        summary.addProperty("state", match.state().name().toLowerCase());
        summary.add("rules", match.rules().toJson());
        return summary;
    }

    public static JsonObject match(BattleBallMatch match, int userId, long now) {
        JsonObject view = summary(match);
        BattleBallMatch.State state = match.state();

        view.addProperty("hostId", match.hostId());
        view.addProperty("youAreHost", match.hostId() == userId);
        view.addProperty("width", match.arena().width());
        view.addProperty("height", match.arena().height());
        view.addProperty("tiles", match.tileString());
        view.addProperty(
                "timeLeft",
                state == BattleBallMatch.State.COUNTDOWN || state == BattleBallMatch.State.PLAYING
                        ? Math.max(0L, match.phaseEndsAt() - now)
                        : 0L);
        view.addProperty("serverTime", now);

        int[] scores = match.teamScores();
        JsonArray teamScores = new JsonArray();

        for (int team = 0; team < match.rules().teams(); team++) teamScores.add(scores[team]);

        view.add("scores", teamScores);

        JsonArray winners = new JsonArray();

        for (int team : match.winners()) winners.add(team);

        view.add("winners", winners);

        JsonArray players = new JsonArray();

        for (BattleBallMatch.Player player : match.players()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("userId", player.userId);
            entry.addProperty("name", player.name);
            entry.addProperty("figure", player.figure);
            entry.addProperty("gender", player.gender);
            entry.addProperty("bot", player.bot);
            entry.addProperty("you", player.userId == userId);
            entry.addProperty("team", player.team);
            entry.addProperty("x", player.x);
            entry.addProperty("y", player.y);
            entry.addProperty("dir", player.dir);
            entry.addProperty("moving", now - player.movedAt < BattleBallMatch.MOVE_MS + 50L);
            entry.addProperty("state", player.state.name().toLowerCase());
            entry.addProperty(
                    "stateLeft",
                    player.state == BattleBallMatch.PlayerState.NORMAL ? 0L : Math.max(0L, player.stateUntil - now));
            entry.addProperty("paintsFor", player.paintsFor());
            entry.addProperty("held", player.held);
            entry.addProperty("heldLeft", player.held == 0 ? 0L : Math.max(0L, player.heldUntil - now));
            entry.addProperty("score", match.playerScore(player.userId));
            players.add(entry);
        }

        view.add("players", players);

        JsonArray powers = new JsonArray();

        for (BattleBallMatch.PowerUp power : match.powerUps()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("id", power.id());
            entry.addProperty("type", power.type());
            entry.addProperty("x", power.x());
            entry.addProperty("y", power.y());
            powers.add(entry);
        }

        view.add("powerUps", powers);

        JsonArray pins = new JsonArray();

        for (BattleBallMatch.Pin pin : match.pins()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("id", pin.id());
            entry.addProperty("x", pin.x());
            entry.addProperty("y", pin.y());
            pins.add(entry);
        }

        view.add("pins", pins);

        JsonArray log = new JsonArray();

        for (BattleBallMatch.Event event : match.log()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("seq", event.seq());
            entry.addProperty("ago", Math.max(0L, now - event.at()));
            entry.addProperty("type", event.type());
            entry.addProperty("userId", event.userId());
            entry.addProperty("a", event.a());
            entry.addProperty("b", event.b());
            entry.addProperty("c", event.c());
            entry.addProperty("d", event.d());
            log.add(entry);
        }

        view.add("log", log);
        return view;
    }
}
