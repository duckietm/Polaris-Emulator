package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Habbo's Poker (the Cunning Fox Gamehall, 2004; rules as Kepler plays them): five-card draw for 2 to 4 players, with
 * the 52 cards and two jokers (wild). Everybody gets five cards, changes as many as they like once, all at the same
 * time; then the hands are shown and the best one wins (equal best hands share the win). No stake: the hands only.
 *
 * <p>Cards: a suit (h, d, c, s) and a face (1 the ace, 2 to 10, 11 jack, 12 queen, 13 king), or "joker" - the names
 * of the original's card pictures.
 */
public final class Poker implements HallGame {

    public static final String JOKER = "joker";
    static final String[] SUITS = {"h", "d", "c", "s"};
    /** The hands, from the weakest (the names' keys in the client: `hall.poker.hand.<n>`). */
    static final int HIGH_CARD = 0;

    static final int PAIR = 1;
    static final int TWO_PAIRS = 2;
    static final int THREE = 3;
    static final int STRAIGHT = 4;
    static final int FLUSH = 5;
    static final int FULL_HOUSE = 6;
    static final int FOUR = 7;
    static final int STRAIGHT_FLUSH = 8;
    static final int ROYAL_FLUSH = 9;
    static final int FIVE = 10;

    private int seats;
    private final List<List<String>> hands = new ArrayList<>();
    private final List<Set<Integer>> changes = new ArrayList<>();
    private final List<Boolean> changed = new ArrayList<>();
    private final List<Boolean> folded = new ArrayList<>();
    private boolean finished;
    private List<Integer> winners = List.of();

    @Override
    public void start(int seats, Random random) {
        this.seats = seats;
        this.hands.clear();
        this.changes.clear();
        this.changed.clear();
        this.folded.clear();
        this.finished = false;
        this.winners = List.of();

        List<String> deck = deck();

        Collections.shuffle(deck, random);
        this.deck = deck;

        for (int seat = 0; seat < seats; seat++) {
            this.hands.add(new ArrayList<>(deck.subList(0, 5)));
            deck.subList(0, 5).clear();
            this.changes.add(new TreeSet<>());
            this.changed.add(false);
            this.folded.add(false);
        }
    }

    private List<String> deck = new ArrayList<>();

    static List<String> deck() {
        List<String> deck = new ArrayList<>();

        deck.add(JOKER);
        deck.add(JOKER);

        for (String suit : SUITS) {
            for (int face = 1; face <= 13; face++) deck.add(suit + face);
        }

        return deck;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (this.finished) throw new HallException("finished");
        if (!this.waitingFor(seat)) throw new HallException("not_your_turn");

        Set<Integer> picked = new TreeSet<>();

        if (move != null && move.has("change") && move.get("change").isJsonArray()) {
            for (JsonElement element : move.getAsJsonArray("change")) {
                int index = element.getAsInt();

                if (index < 0 || index > 4) throw new HallException("bad_move");

                picked.add(index);
            }
        }

        this.change(seat, picked);
    }

    private void change(int seat, Set<Integer> picked) {
        List<String> hand = this.hands.get(seat);

        for (int index : picked) hand.set(index, this.deck.remove(0));

        this.changes.set(seat, picked);
        this.changed.set(seat, true);

        if (this.everybodyChanged()) this.showdown();
    }

    private boolean everybodyChanged() {
        for (int seat = 0; seat < this.seats; seat++) {
            if (!this.changed.get(seat) && !this.folded.get(seat)) return false;
        }

        return true;
    }

    private void showdown() {
        long best = -1;
        List<Integer> top = new ArrayList<>();

        for (int seat = 0; seat < this.seats; seat++) {
            if (this.folded.get(seat)) continue;

            long value = value(this.hands.get(seat));

            if (value > best) {
                best = value;
                top.clear();
            }

            if (value == best) top.add(seat);
        }

        this.winners = top;
        this.finished = true;
    }

    @Override
    public boolean waitingFor(int seat) {
        return !this.finished && seat < this.seats && !this.changed.get(seat) && !this.folded.get(seat);
    }

    /** The robot keeps what makes a hand (jokers, pairs and more, four of a suit, a straight) and changes the rest. */
    @Override
    public void bot(int seat, Random random) {
        List<String> hand = this.hands.get(seat);
        int kind = (int) (value(hand) / POWER);
        Set<Integer> keep = new TreeSet<>();

        if (kind >= STRAIGHT) {
            for (int i = 0; i < 5; i++) keep.add(i);
        } else {
            int[] counts = new int[15];

            for (String card : hand) {
                if (!card.equals(JOKER)) counts[face(card)]++;
            }

            for (int i = 0; i < 5; i++) {
                String card = hand.get(i);

                if (card.equals(JOKER) || counts[face(card)] >= 2) keep.add(i);
            }

            for (String suit : SUITS) {
                List<Integer> same = new ArrayList<>();

                for (int i = 0; i < 5; i++) {
                    if (hand.get(i).startsWith(suit) || hand.get(i).equals(JOKER)) same.add(i);
                }

                if (same.size() >= 4 && keep.size() <= 2) {
                    keep.clear();
                    keep.addAll(same);
                }
            }

            if (keep.isEmpty()) {
                // Nothing yet: the highest card (and the next, now and then).
                List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4));

                order.sort((a, b) -> face(hand.get(b)) - face(hand.get(a)));
                keep.add(order.get(0));
                if (random.nextBoolean()) keep.add(order.get(1));
            }
        }

        Set<Integer> picked = new TreeSet<>();

        for (int i = 0; i < 5; i++) {
            if (!keep.contains(i)) picked.add(i);
        }

        this.change(seat, picked);
    }

    @Override
    public void forfeit(int seat) {
        if (this.finished || seat >= this.seats) return;

        this.folded.set(seat, true);

        int left = 0;
        int last = -1;

        for (int other = 0; other < this.seats; other++) {
            if (!this.folded.get(other)) {
                left++;
                last = other;
            }
        }

        if (left == 1) {
            this.winners = List.of(last);
            this.finished = true;
        } else if (this.everybodyChanged()) {
            this.showdown();
        }
    }

    @Override
    public boolean finished() {
        return this.finished;
    }

    @Override
    public List<Integer> winners() {
        return this.winners;
    }

    // ---- The hands

    private static final long POWER = 15L * 15 * 15 * 15 * 15;

    static int face(String card) {
        if (card.equals(JOKER)) return 0;

        int face = Integer.parseInt(card.substring(1));

        return face == 1 ? 14 : face;
    }

    /** A hand's worth: its kind first, then its faces in order of importance (jokers are worth their best card). */
    static long value(List<String> hand) {
        List<Integer> jokers = new ArrayList<>();

        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).equals(JOKER)) jokers.add(i);
        }

        if (jokers.isEmpty()) return plain(hand);

        long best = -1;
        List<String> trial = new ArrayList<>(hand);

        for (String a : deck()) {
            if (a.equals(JOKER)) continue;

            trial.set(jokers.get(0), a);

            if (jokers.size() == 1) {
                best = Math.max(best, plain(trial));
                continue;
            }

            for (String b : deck()) {
                if (b.equals(JOKER)) continue;

                trial.set(jokers.get(1), b);
                best = Math.max(best, plain(trial));
            }
        }

        return best;
    }

    /** A hand without jokers (the same card twice: five of a kind is only possible with a joker). */
    private static long plain(List<String> hand) {
        int[] faces = new int[5];
        boolean flush = true;

        for (int i = 0; i < 5; i++) {
            faces[i] = face(hand.get(i));

            if (!hand.get(i).substring(0, 1).equals(hand.get(0).substring(0, 1))) flush = false;
        }

        int[] counts = new int[15];

        for (int face : faces) counts[face]++;

        // The faces by how many there are, then by height: what the tie-break compares.
        Integer[] order = Arrays.stream(faces).boxed().toArray(Integer[]::new);

        Arrays.sort(order, (a, b) -> counts[b] != counts[a] ? counts[b] - counts[a] : b - a);

        int[] sorted = Arrays.stream(faces).sorted().toArray();
        boolean distinct = Arrays.stream(sorted).distinct().count() == 5;
        boolean straight = distinct && sorted[4] - sorted[0] == 4;
        boolean wheel = distinct && sorted[4] == 14 && sorted[3] == 5;

        if (wheel) {
            straight = true;
            order = new Integer[] {5, 4, 3, 2, 1};
        }

        int top = counts[order[0]];
        int second = top < 5 ? counts[order[top]] : 0;
        int kind;

        if (top == 5) kind = FIVE;
        else if (straight && flush) kind = sorted[0] == 10 ? ROYAL_FLUSH : STRAIGHT_FLUSH;
        else if (top == 4) kind = FOUR;
        else if (top == 3 && second == 2) kind = FULL_HOUSE;
        else if (flush) kind = FLUSH;
        else if (straight) kind = STRAIGHT;
        else if (top == 3) kind = THREE;
        else if (top == 2 && second == 2) kind = TWO_PAIRS;
        else if (top == 2) kind = PAIR;
        else kind = HIGH_CARD;

        long value = kind;

        for (int face : order) value = value * 15 + face;

        return value;
    }

    static int kind(List<String> hand) {
        return (int) (value(hand) / POWER);
    }

    @Override
    public JsonObject view(int seat) {
        JsonObject view = new JsonObject();

        view.addProperty("turn", -1);
        view.addProperty("phase", this.finished ? "showdown" : "change");

        JsonArray players = new JsonArray();

        for (int other = 0; other < this.seats; other++) {
            JsonObject entry = new JsonObject();
            JsonArray cards = new JsonArray();
            boolean shown = this.finished || other == seat;

            for (String card : this.hands.get(other)) cards.add(shown ? card : "");

            entry.add("cards", cards);
            entry.addProperty("changed", this.changed.get(other));
            entry.addProperty(
                    "changes", this.changed.get(other) ? this.changes.get(other).size() : -1);
            entry.addProperty("folded", this.folded.get(other));
            entry.addProperty("hand", shown && !this.folded.get(other) ? kind(this.hands.get(other)) : -1);
            players.add(entry);
        }

        view.add("hands", players);
        return view;
    }

    List<String> hand(int seat) {
        return this.hands.get(seat);
    }
}
