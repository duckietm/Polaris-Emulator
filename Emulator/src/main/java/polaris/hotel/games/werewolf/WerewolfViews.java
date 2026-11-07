package polaris.hotel.games.werewolf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A village as JSON: in the list of villages, and for one of its players, who sees only what their role lets them see
 * (their own role, their pack, their lover, what the seer found, the dead when roles are shown, everything at the end).
 */
public final class WerewolfViews {

    private static final int LOG_VIEW = 60;

    private WerewolfViews() {}

    public static JsonObject summary(WerewolfGame game) {
        JsonObject summary = new JsonObject();
        WerewolfGame.Seat host = game.seat(game.hostId());

        summary.addProperty("id", game.id());
        summary.addProperty("host", host == null ? "" : host.name);
        summary.addProperty("players", game.seats().size());
        summary.addProperty("maxPlayers", game.rules().maxPlayers());
        summary.addProperty("state", game.state().name().toLowerCase());
        summary.add("rules", game.rules().toJson());
        return summary;
    }

    /** Whether this player's camp won. */
    public static boolean won(WerewolfGame game, WerewolfGame.Seat seat) {
        return switch (game.winner()) {
            case "village" -> !seat.role.wolf();
            case "wolves" -> seat.role.wolf();
            case "lovers" -> seat.lover != 0;
            default -> false;
        };
    }

    public static JsonObject game(WerewolfGame game, int userId, long now) {
        JsonObject view = summary(game);
        WerewolfGame.Seat me = game.seat(userId);
        boolean playing = game.state() == WerewolfGame.State.PLAYING;
        boolean finished = game.state() == WerewolfGame.State.FINISHED;
        boolean actor = playing && me != null && game.isActor(me);
        WerewolfGame.Step step = game.step();

        view.addProperty("hostId", game.hostId());
        view.addProperty("youAreHost", game.hostId() == userId);
        view.addProperty("step", step.code());
        view.addProperty("night", game.night());
        view.addProperty("isNight", game.isNight());
        view.addProperty("secondsLeft", playing ? Math.max(0L, (game.stepEndsAt() - now + 999L) / 1000L) : 0L);
        view.addProperty("winner", game.winner());

        Map<Integer, Set<String>> wolfChoices = me != null && me.role.wolf() ? game.wolfChoices() : Map.of();
        Map<Integer, Integer> votes = game.votes();
        JsonArray seats = new JsonArray();

        for (WerewolfGame.Seat seat : game.seats()) {
            JsonObject entry = new JsonObject();
            String role = roleSeen(game, me, seat, finished);

            entry.addProperty("userId", seat.userId);
            entry.addProperty("chair", seat.chair);
            entry.addProperty("name", seat.name);
            entry.addProperty("figure", seat.figure);
            entry.addProperty("gender", seat.gender);
            entry.addProperty("bot", seat.bot);
            entry.addProperty("alive", game.state() == WerewolfGame.State.WAITING || seat.alive);
            entry.addProperty("you", seat.userId == userId);
            entry.addProperty("role", role);
            entry.addProperty(
                    "lover",
                    me != null
                            && (me.lover == seat.userId
                                    || (seat == me && me.lover != 0)
                                    || finished && seat.lover != 0));
            entry.addProperty("chosen", me != null && (me.choice == seat.userId || me.second == seat.userId) && actor);
            entry.addProperty("votes", votes.getOrDefault(seat.userId, 0));
            entry.addProperty("ready", step == WerewolfGame.Step.DEBATE && seat.ready);

            JsonArray wolves = new JsonArray();

            for (String wolf : wolfChoices.getOrDefault(seat.userId, Set.of())) wolves.add(wolf);

            entry.add("wolves", wolves);
            entry.addProperty("targetable", actor && targetable(game, me, seat));
            seats.add(entry);
        }

        view.add("seats", seats);

        JsonObject you = new JsonObject();

        if (me != null) {
            you.addProperty("role", me.role.code());
            you.addProperty("alive", game.state() == WerewolfGame.State.WAITING || me.alive);
            you.addProperty("seated", true);
            you.addProperty("won", finished && won(game, me));
            you.addProperty("action", actor ? action(step) : "");
            you.addProperty("done", actor && (step == WerewolfGame.Step.DEBATE ? me.ready : me.done));
            you.addProperty("lifePotion", me.role == WerewolfRole.WITCH && game.lifePotion());
            you.addProperty("deathPotion", me.role == WerewolfRole.WITCH && game.deathPotion());

            WerewolfGame.Seat prey = game.seat(game.wolfVictim());

            you.addProperty("victim", actor && step == WerewolfGame.Step.WITCH && prey != null ? prey.name : "");
            you.addProperty("victimId", actor && step == WerewolfGame.Step.WITCH && prey != null ? prey.userId : 0);

            WerewolfGame.Channel channel = game.chatChannel(me);

            you.addProperty("chat", channel == null ? "" : channel.name().toLowerCase());
        } else {
            you.addProperty("seated", false);
            you.addProperty("chat", "");
        }

        view.add("you", you);

        JsonArray log = new JsonArray();
        List<WerewolfGame.Event> events = game.log();
        int from = 0;
        int count = 0;

        for (int index = events.size() - 1; index >= 0; index--) {
            if (game.visible(events.get(index), me) && ++count >= LOG_VIEW) {
                from = index;
                break;
            }
        }

        for (int index = from; index < events.size(); index++) {
            WerewolfGame.Event event = events.get(index);

            if (!game.visible(event, me)) continue;

            JsonObject entry = new JsonObject();

            entry.addProperty("seq", event.seq());
            entry.addProperty("type", event.type());
            entry.addProperty("channel", event.channel().name().toLowerCase());
            entry.addProperty("a", event.a());
            entry.addProperty("b", event.b());
            entry.addProperty("c", event.c());
            log.add(entry);
        }

        view.add("log", log);
        return view;
    }

    /** The role this player sees on that seat; empty when hidden. */
    private static String roleSeen(WerewolfGame game, WerewolfGame.Seat me, WerewolfGame.Seat seat, boolean finished) {
        if (game.state() == WerewolfGame.State.WAITING) return "";
        if (finished || seat == me) return seat.role.code();
        if (!seat.alive && game.rules().revealRoles()) return seat.role.code();
        if (me == null) return "";
        if (me.role.wolf() && seat.role.wolf()) return seat.role.code();
        if (me.lover == seat.userId) return seat.role.code();
        if (me.seen.containsKey(seat.userId)) return seat.role.code();

        return "";
    }

    private static String action(WerewolfGame.Step step) {
        return switch (step) {
            case CUPID, GUARD, SEER, WOLVES, WITCH, VOTE, HUNTER -> step.code();
            case DEBATE -> "ready";
            default -> "";
        };
    }

    private static boolean targetable(WerewolfGame game, WerewolfGame.Seat me, WerewolfGame.Seat seat) {
        if (!seat.alive) return false;

        return switch (game.step()) {
            case CUPID -> true;
            case GUARD -> seat.userId != game.lastProtectedId();
            case SEER -> seat != me && !me.done;
            case WOLVES -> !seat.role.wolf();
            case WITCH -> seat != me && game.deathPotion();
            case VOTE, HUNTER -> seat != me;
            default -> false;
        };
    }
}
