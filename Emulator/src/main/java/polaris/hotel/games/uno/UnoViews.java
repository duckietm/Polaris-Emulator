package polaris.hotel.games.uno;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** UNO as JSON: a table for one of its players (only their own cards), and a table in the list of tables. */
public final class UnoViews {

    private UnoViews() {}

    public static JsonObject summary(UnoTable table) {
        JsonObject summary = new JsonObject();
        UnoTable.Seat host = table.seat(table.hostId());

        summary.addProperty("id", table.id());
        summary.addProperty("host", host == null ? "" : host.name);
        summary.addProperty("players", table.seats().size());
        summary.addProperty("maxPlayers", table.maxPlayers());
        summary.addProperty("state", table.state().name().toLowerCase());
        return summary;
    }

    public static JsonObject table(UnoTable table, int userId, long now) {
        JsonObject view = summary(table);
        boolean playing = table.state() == UnoTable.State.PLAYING;
        UnoTable.Seat me = table.seat(userId);
        int myIndex = table.seats().indexOf(me);
        boolean myTurn = playing && myIndex == table.turn();

        view.addProperty("hostId", table.hostId());
        view.addProperty("youAreHost", table.hostId() == userId);

        JsonArray seats = new JsonArray();

        for (int index = 0; index < table.seats().size(); index++) {
            UnoTable.Seat seat = table.seats().get(index);
            JsonObject entry = new JsonObject();

            entry.addProperty("userId", seat.userId);
            entry.addProperty("name", seat.name);
            entry.addProperty("figure", seat.figure);
            entry.addProperty("gender", seat.gender);
            entry.addProperty("bot", seat.bot);
            entry.addProperty("cards", seat.hand.size());
            entry.addProperty("declared", seat.declared);
            entry.addProperty("catchable", playing && seat.userId != userId && UnoTable.catchable(seat));
            entry.addProperty("turn", playing && index == table.turn());
            entry.addProperty("you", seat.userId == userId);
            seats.add(entry);
        }

        view.add("seats", seats);
        view.addProperty("top", table.top());
        view.addProperty("colour", String.valueOf(table.colour()));
        view.addProperty("direction", table.direction());
        view.addProperty("deck", table.deckSize());
        view.addProperty("turnSecondsLeft", playing ? Math.max(0L, (table.turnEndsAt() - now + 999L) / 1000L) : 0L);
        view.addProperty("yourTurn", myTurn);
        view.addProperty("drew", myTurn && table.drew());

        JsonArray hand = new JsonArray();

        if (me != null) {
            for (String card : me.hand) {
                JsonObject entry = new JsonObject();
                boolean playable = myTurn
                        && (!table.drew() || card.equals(table.drawnCard()))
                        && UnoCards.playable(card, me.hand, table.top(), table.colour());

                entry.addProperty("code", card);
                entry.addProperty("playable", playable);
                hand.add(entry);
            }
        }

        view.add("hand", hand);
        view.addProperty("canDraw", myTurn && !table.drew());
        view.addProperty("canPass", myTurn && table.drew());
        view.addProperty(
                "canUno",
                playing && me != null && !me.declared && (me.hand.size() == 1 || (me.hand.size() == 2 && myTurn)));

        if (table.winnerId() != 0) {
            JsonObject winner = new JsonObject();

            winner.addProperty("userId", table.winnerId());
            winner.addProperty("name", table.winnerName());
            view.add("winner", winner);
        }

        JsonArray log = new JsonArray();

        for (UnoTable.Event event : table.log()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("type", event.type());
            entry.addProperty("name", event.name());
            entry.addProperty("arg", event.arg());
            log.add(entry);
        }

        view.add("log", log);
        view.addProperty("version", table.version());
        return view;
    }
}
