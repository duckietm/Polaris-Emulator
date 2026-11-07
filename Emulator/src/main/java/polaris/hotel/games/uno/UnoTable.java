package polaris.hotel.games.uno;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import polaris.hotel.games.GameTexts;

/**
 * One UNO table: eight chairs around it, the players and robots sitting on them, the deck, the pile, whose turn it is
 * and the rules its host chose. Turns go round the chairs in order. Every action takes the time in milliseconds, so the
 * rules run the same in the tests. Not thread-safe: the manager locks the table.
 */
public final class UnoTable {

    public enum State {
        WAITING,
        PLAYING,
        FINISHED
    }

    /** A player at the table; a robot has a negative id. */
    public static final class Seat {
        final int userId;
        final String name;
        final String figure;
        final String gender;
        final boolean bot;
        final List<String> hand = new ArrayList<>();
        int chair;
        boolean declared;
        /** When the player was left with one card without saying UNO (0: not exposed). */
        long exposedAt;

        boolean exposureChecked;
        long lastSeen;
        int missedTurns;
        /** The order in which the players came: the first one still here is the host. */
        long joined;

        Seat(int userId, String name, String figure, String gender, boolean bot, int chair, long now) {
            this.userId = userId;
            this.name = name;
            this.figure = figure;
            this.gender = gender;
            this.bot = bot;
            this.chair = chair;
            this.lastSeen = now;
        }

        public int userId() {
            return this.userId;
        }

        public boolean bot() {
            return this.bot;
        }

        public List<String> hand() {
            return this.hand;
        }
    }

    /** Something that happened: its number (to show each once), its kind, who, and a card or a count. */
    public record Event(long seq, String type, String name, String arg) {}

    public static final int MIN_PLAYERS = 2;
    private static final int LOG_SIZE = 8;
    private static final int MAX_MISSED_TURNS = 3;
    private static final long BOT_DELAY_MS = 1300L;
    private static final long BOT_CATCH_MS = 2500L;
    private static final String[] BOT_NAMES = {"Robot Lulu", "Robot Toto", "Robot Zaza", "Robot Kiki", "Robot Momo"};
    private static final String[] BOT_FIGURES = {
        "hr-828-45.hd-180-1.ch-3215-92.lg-3023-110.sh-305-62",
        "hr-515-33.hd-600-1.ch-635-73.lg-716-82.sh-735-68",
        "hr-893-48.hd-180-2.ch-3030-81.lg-275-82.sh-290-91",
        "hr-545-61.hd-600-10.ch-665-71.lg-696-64.sh-725-62",
        "hr-170-42.hd-190-8.ch-215-66.lg-280-110.sh-300-64"
    };
    private static final String[] BOT_GENDERS = {"M", "F", "M", "F", "M"};

    private final int id;
    private final Random random;
    private final List<Seat> seats = new ArrayList<>();
    private final Deque<String> deck = new ArrayDeque<>();
    private final List<String> pile = new ArrayList<>();
    private final Deque<Event> log = new ArrayDeque<>();
    private UnoRules rules;
    private State state = State.WAITING;
    private char colour = 'R';
    private int turn;
    private int direction = 1;
    private long turnEndsAt;
    private long botActsAt;
    private boolean drew;
    private String drawnCard;
    /** Cards waiting for the player whose turn it is (stacked +2 and +4). */
    private int pendingDraw;

    private int winnerId;
    private String winnerName = "";
    private long version;
    private long eventSeq;
    private int nextBotId = -1;
    private long joins;

    public UnoTable(int id, UnoRules rules, Random random) {
        this.id = id;
        this.rules = rules;
        this.random = random;
    }

    // ---- Seats

    /** Sits a player down, on the chair asked for when it is free, otherwise on the first free one. */
    public void join(int userId, String name, String figure, String gender, int chair, long now) {
        if (this.state == State.PLAYING) throw new UnoException("already_started");
        if (this.seat(userId) != null) return;
        if (this.seats.size() >= this.rules.maxPlayers()) throw new UnoException("table_full");

        Seat seat = new Seat(userId, name, figure, gender, false, this.freeChair(chair), now);

        seat.joined = ++this.joins;
        this.seats.add(seat);
        this.sortSeats();
        this.event("join", name, "");
    }

    /** Moves a player to another free chair, before the game. */
    public void sit(int userId, int chair) {
        Seat seat = this.seat(userId);

        if (seat == null) throw new UnoException("no_table");
        if (this.state == State.PLAYING) throw new UnoException("already_started");
        if (chair < 0 || chair >= UnoRules.MAX_CHAIRS || this.chairTaken(chair)) throw new UnoException("chair_taken");

        seat.chair = chair;
        this.sortSeats();
        this.version++;
    }

    public void addBot(int hostId, int chair, long now) {
        this.requireHost(hostId);
        if (!this.rules.bots()) throw new UnoException("no_bots");
        if (this.state == State.PLAYING) throw new UnoException("already_started");
        if (this.seats.size() >= this.rules.maxPlayers()) throw new UnoException("table_full");

        String[] names = GameTexts.botNames("uno", BOT_NAMES);
        int index = (-this.nextBotId - 1) % BOT_FIGURES.length;
        Seat bot = new Seat(
                this.nextBotId--,
                names[(-this.nextBotId - 1) % names.length],
                BOT_FIGURES[index],
                BOT_GENDERS[index],
                true,
                this.freeChair(chair),
                now);

        this.seats.add(bot);
        this.sortSeats();
        this.event("join", bot.name, "");
    }

    public void removeBot(int hostId, int botId) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new UnoException("already_started");

        Seat bot = this.seat(botId);

        if (bot == null || !bot.bot) throw new UnoException("no_bot");

        this.seats.remove(bot);
        this.event("leave", bot.name, "");
    }

    /** The host changes the rules before the game; robots go when they are no longer allowed. */
    public void setRules(int hostId, UnoRules next) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new UnoException("already_started");
        if (next.maxPlayers() < this.seats.size() && (next.bots() || this.humans() > next.maxPlayers())) {
            throw new UnoException("too_many_seated");
        }

        this.rules = next;

        if (!next.bots()) this.seats.removeIf(seat -> seat.bot);

        while (this.seats.size() > next.maxPlayers()) this.seats.remove(this.seats.size() - 1);

        this.event("rules", "", "");
    }

    /** A player leaves: during a game their cards go under the deck, and a last player standing wins. */
    public void leave(int userId) {
        Seat seat = this.seat(userId);

        if (seat == null) return;

        int index = this.seats.indexOf(seat);

        this.seats.remove(index);
        this.event("leave", seat.name, "");

        if (this.state != State.PLAYING) return;

        this.deck.addAll(seat.hand);

        if (this.seats.size() < MIN_PLAYERS) {
            if (!this.seats.isEmpty()) this.win(this.seats.get(0));
            return;
        }

        if (index < this.turn) {
            this.turn--;
        } else if (index == this.turn) {
            if (this.direction < 0) this.turn--;
            this.turn = Math.floorMod(this.turn, this.seats.size());
            this.startTurn(System.currentTimeMillis());
            return;
        }

        this.turn = Math.floorMod(this.turn, this.seats.size());
    }

    public void seen(int userId, long now) {
        Seat seat = this.seat(userId);

        if (seat != null) seat.lastSeen = now;
    }

    // ---- The game

    public void start(int hostId, long now) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new UnoException("already_started");
        if (this.seats.size() < MIN_PLAYERS) throw new UnoException("not_enough_players");

        List<String> cards = UnoCards.deck();

        Collections.shuffle(cards, this.random);
        this.deck.clear();
        this.deck.addAll(cards);
        this.pile.clear();
        this.log.clear();
        this.winnerId = 0;
        this.winnerName = "";
        this.direction = 1;
        this.pendingDraw = 0;

        for (Seat seat : this.seats) {
            seat.hand.clear();
            seat.declared = false;
            seat.exposedAt = 0L;
            seat.missedTurns = 0;

            for (int card = 0; card < this.rules.handSize(); card++) seat.hand.add(this.deck.pollFirst());
        }

        // The first card of the pile is a number card.
        String first = this.deck.pollFirst();

        while (!UnoCards.isNumber(first)) {
            this.deck.addLast(first);
            first = this.deck.pollFirst();
        }

        this.pile.add(first);
        this.colour = first.charAt(0);
        this.state = State.PLAYING;
        this.turn = this.random.nextInt(this.seats.size());
        this.event("start", "", first);
        this.startTurn(now);
    }

    /**
     * Plays a card: on one's turn, or out of turn with the very same card as the pile when the table jumps in (the turn
     * then goes on from that player).
     */
    public void play(int userId, String card, char chosenColour, long now) {
        if (this.state != State.PLAYING) throw new UnoException("not_playing");

        Seat seat = this.seat(userId);

        if (seat == null) throw new UnoException("not_playing");
        if (!UnoCards.isCard(card) || !seat.hand.contains(card)) throw new UnoException("no_card");

        int index = this.seats.indexOf(seat);
        boolean jumping = index != this.turn;

        if (jumping) {
            if (!this.mayJumpIn(seat, card)) throw new UnoException("not_your_turn");
        } else {
            if (this.drew && !card.equals(this.drawnCard)) throw new UnoException("drawn_only");
            if (!this.playable(seat, card)) throw new UnoException(this.refusal(seat, card));
        }

        if (UnoCards.isWild(card) && !UnoCards.isColour(chosenColour)) throw new UnoException("choose_colour");

        if (jumping) {
            this.turn = index;
            this.event("jump", seat.name, card);
        }

        seat.hand.remove(card);
        seat.missedTurns = 0;
        this.pile.add(card);
        this.colour = UnoCards.isWild(card) ? chosenColour : card.charAt(0);
        this.event("play", seat.name, UnoCards.isWild(card) ? card + chosenColour : card);

        if (seat.hand.isEmpty()) {
            this.win(seat);
            return;
        }

        if (seat.hand.size() == 1 && !seat.declared && this.rules.unoPenalty()) {
            seat.exposedAt = now;
            seat.exposureChecked = false;
        }

        char value = card.charAt(1);
        boolean twoPlayers = this.seats.size() == 2;
        boolean stacking = this.rules.stackTwo() || this.rules.stackFour();

        if (value == 'R') {
            if (twoPlayers) {
                this.advance(2, now);
            } else {
                this.direction = -this.direction;
                this.advance(1, now);
            }
        } else if (value == 'S') {
            this.advance(2, now);
        } else if (value == 'D' || value == 'F') {
            int count = value == 'D' ? 2 : 4;

            if (stacking) {
                // The next player stacks a card on it or draws them all.
                this.pendingDraw += count;
                this.advance(1, now);
            } else {
                Seat next = this.seats.get(this.index(1));

                this.give(next, count);
                this.event("draw_penalty", next.name, String.valueOf(count));
                this.advance(2, now);
            }
        } else {
            this.advance(1, now);
        }
    }

    /**
     * Draws: the stacked cards when some wait (then the turn passes), otherwise one card (or until one fits, with that
     * rule); a card that cannot be played ends the turn, a playable one may be played or kept.
     */
    public void draw(int userId, long now) {
        Seat seat = this.current(userId);

        if (this.drew) throw new UnoException("already_drew");

        seat.missedTurns = 0;

        if (this.pendingDraw > 0) {
            int count = this.pendingDraw;

            this.pendingDraw = 0;
            this.give(seat, count);
            this.event("draw_penalty", seat.name, String.valueOf(count));
            this.advance(1, now);
            return;
        }

        String drawn = null;
        int count = 0;

        do {
            List<String> given = this.give(seat, 1);

            if (given.isEmpty()) break;

            count++;
            drawn = given.get(0);
        } while (this.rules.drawUntilPlayable() && !this.playable(seat, drawn) && count < 30);

        this.event("draw", seat.name, String.valueOf(count));

        if (drawn == null || !this.playable(seat, drawn)) {
            this.advance(1, now);
            return;
        }

        this.drew = true;
        this.drawnCard = drawn;
        this.version++;
    }

    public void pass(int userId, long now) {
        this.current(userId);

        if (!this.drew) throw new UnoException("draw_first");

        this.advance(1, now);
    }

    /** "UNO!": said with one card left, or with two on one's own turn, just before playing. */
    public void declare(int userId) {
        Seat seat = this.seat(userId);

        if (seat == null || this.state != State.PLAYING) throw new UnoException("not_playing");

        boolean ownTurn = this.seats.indexOf(seat) == this.turn;

        if (!(seat.hand.size() == 1 || (seat.hand.size() == 2 && ownTurn))) throw new UnoException("too_many_cards");
        if (seat.declared) return;

        seat.declared = true;
        seat.exposedAt = 0L;
        this.event("uno", seat.name, "");
    }

    /** A player left with one card who has not said UNO draws two cards. */
    public void catchUno(int catcherId, int targetId) {
        Seat catcher = this.seat(catcherId);
        Seat target = this.seat(targetId);

        if (catcher == null || target == null || this.state != State.PLAYING) throw new UnoException("not_playing");
        if (!this.rules.unoPenalty() || !catchable(target)) throw new UnoException("no_catch");

        target.exposedAt = 0L;
        this.give(target, 2);
        this.event("caught", target.name, catcher.name);
    }

    /**
     * The clock: a player who let the time run out draws (the stacked cards too) and passes (three times running, they
     * leave), robots play, jump in and catch a player who forgot UNO. Answers the ids of the players who were removed.
     */
    public List<Integer> tick(long now) {
        List<Integer> removed = new ArrayList<>();

        if (this.state != State.PLAYING || this.seats.isEmpty()) return removed;

        this.botCatches(now);
        if (this.state == State.PLAYING) this.botJumps(now);
        if (this.state != State.PLAYING) return removed;

        Seat seat = this.seats.get(this.turn);

        if (seat.bot) {
            if (now >= this.botActsAt) this.botPlays(seat, now);
        } else if (now >= this.turnEndsAt) {
            seat.missedTurns++;

            if (seat.missedTurns >= MAX_MISSED_TURNS) {
                removed.add(seat.userId);
                this.leave(seat.userId);
            } else if (this.pendingDraw > 0) {
                int missed = seat.missedTurns;

                this.draw(seat.userId, now);
                seat.missedTurns = missed;
            } else {
                if (!this.drew) this.give(seat, 1);
                this.event("timeout", seat.name, "");
                this.advance(1, now);
            }
        }

        return removed;
    }

    private void botCatches(long now) {
        if (!this.rules.unoPenalty()) return;

        Seat catcher = null;

        for (Seat seat : this.seats) {
            if (seat.bot) {
                catcher = seat;
                break;
            }
        }

        if (catcher == null) return;

        for (Seat seat : new ArrayList<>(this.seats)) {
            if (seat == catcher || !catchable(seat) || seat.exposureChecked) continue;
            if (now - seat.exposedAt < BOT_CATCH_MS) continue;

            seat.exposureChecked = true;

            if (this.random.nextBoolean()) this.catchUno(catcher.userId, seat.userId);
        }
    }

    /** A robot holding the very same card as the pile sometimes jumps in. */
    private void botJumps(long now) {
        if (!this.rules.jumpIn() || now - this.turnEndsAt + this.turnMs() < 700L) return;

        for (int index = 0; index < this.seats.size(); index++) {
            Seat seat = this.seats.get(index);

            if (!seat.bot || index == this.turn || !seat.hand.contains(this.top())) continue;
            if (!this.mayJumpIn(seat, this.top()) || this.random.nextInt(100) >= 15) continue;

            this.play(seat.userId, this.top(), ' ', now);
            return;
        }
    }

    private void botPlays(Seat bot, long now) {
        List<String> choices = new ArrayList<>();

        for (String card : bot.hand) {
            if (this.drew && !card.equals(this.drawnCard)) continue;
            if (this.playable(bot, card)) choices.add(card);
        }

        if (choices.isEmpty()) {
            if (this.drew) this.pass(bot.userId, now);
            else this.draw(bot.userId, now);
            return;
        }

        String card = UnoBot.choose(choices, this.colour);

        if (bot.hand.size() == 2 && this.random.nextInt(100) < 85) this.declare(bot.userId);

        this.play(bot.userId, card, UnoBot.favouriteColour(bot.hand, card, this.random), now);
    }

    // ---- What may be played

    /** Whether this card of the player whose turn it is can go on the pile now, with the table's rules. */
    public boolean playable(Seat seat, String card) {
        if (this.rules.noSpecialFinish() && seat.hand.size() == 1 && !UnoCards.isNumber(card)) return false;

        if (this.pendingDraw > 0) {
            char top = this.top().charAt(1);

            if (card.equals("WF")) return this.rules.stackFour();

            return card.charAt(1) == 'D' && top == 'D' && this.rules.stackTwo();
        }

        return UnoCards.playable(card, seat.hand, this.top(), this.colour);
    }

    /** Out of turn: the very same card as the pile (a number or an action, not a wild), with nothing stacked. */
    public boolean mayJumpIn(Seat seat, String card) {
        return this.rules.jumpIn()
                && this.state == State.PLAYING
                && this.pendingDraw == 0
                && !UnoCards.isWild(card)
                && card.equals(this.top())
                && !(this.rules.noSpecialFinish() && seat.hand.size() == 1 && !UnoCards.isNumber(card));
    }

    private String refusal(Seat seat, String card) {
        if (this.rules.noSpecialFinish() && seat.hand.size() == 1 && !UnoCards.isNumber(card)) return "special_finish";
        if (this.pendingDraw > 0) return "must_stack";
        if ("WF".equals(card)) return "draw_four_rule";

        return "not_playable";
    }

    // ---- Turns

    private void advance(int steps, long now) {
        this.turn = this.index(steps);
        this.startTurn(now);
    }

    private int index(int steps) {
        return Math.floorMod(this.turn + steps * this.direction, this.seats.size());
    }

    private long turnMs() {
        return this.rules.turnSeconds() * 1000L;
    }

    private void startTurn(long now) {
        this.drew = false;
        this.drawnCard = null;
        this.turnEndsAt = now + this.turnMs();
        this.botActsAt = now + BOT_DELAY_MS + this.random.nextInt(900);
        this.version++;
    }

    private void win(Seat seat) {
        this.state = State.FINISHED;
        this.winnerId = seat.userId;
        this.winnerName = seat.name;
        this.pendingDraw = 0;
        this.event("win", seat.name, "");
    }

    /** Deals cards from the deck, which takes back the pile (all but its top card) when it runs out. */
    private List<String> give(Seat seat, int count) {
        List<String> given = new ArrayList<>();

        for (int card = 0; card < count; card++) {
            if (this.deck.isEmpty() && this.pile.size() > 1) {
                List<String> back = new ArrayList<>(this.pile.subList(0, this.pile.size() - 1));
                String top = this.top();

                Collections.shuffle(back, this.random);
                this.deck.addAll(back);
                this.pile.clear();
                this.pile.add(top);
            }

            String drawn = this.deck.pollFirst();

            if (drawn == null) break;

            seat.hand.add(drawn);
            given.add(drawn);
        }

        if (seat.hand.size() > 1) {
            seat.declared = false;
            seat.exposedAt = 0L;
        }

        this.version++;
        return given;
    }

    private void event(String type, String name, String arg) {
        this.log.addLast(new Event(++this.eventSeq, type, name, arg));

        while (this.log.size() > LOG_SIZE) this.log.pollFirst();

        this.version++;
    }

    // ---- Chairs

    private int freeChair(int wanted) {
        if (wanted >= 0 && wanted < UnoRules.MAX_CHAIRS && !this.chairTaken(wanted)) return wanted;

        // Spread out: the first free chair in an order that keeps the players facing each other.
        for (int chair : new int[] {0, 4, 2, 6, 1, 5, 3, 7}) {
            if (!this.chairTaken(chair)) return chair;
        }

        throw new UnoException("table_full");
    }

    private boolean chairTaken(int chair) {
        for (Seat seat : this.seats) {
            if (seat.chair == chair) return true;
        }

        return false;
    }

    private void sortSeats() {
        this.seats.sort(Comparator.comparingInt(seat -> seat.chair));
        this.version++;
    }

    private int humans() {
        int count = 0;

        for (Seat seat : this.seats) {
            if (!seat.bot) count++;
        }

        return count;
    }

    // ---- Lookups

    private Seat current(int userId) {
        if (this.state != State.PLAYING) throw new UnoException("not_playing");

        Seat seat = this.seats.get(this.turn);

        if (seat.userId != userId) throw new UnoException("not_your_turn");

        return seat;
    }

    private void requireHost(int userId) {
        if (this.hostId() != userId) throw new UnoException("not_host");
    }

    static boolean catchable(Seat seat) {
        return seat.hand.size() == 1 && !seat.declared && seat.exposedAt > 0L;
    }

    public Seat seat(int userId) {
        for (Seat seat : this.seats) {
            if (seat.userId == userId) return seat;
        }

        return null;
    }

    /** The host is the first player (not robot) who sat down, in chair order. */
    /** The host: the player who came first among those still here (changing chairs does not change the host). */
    public int hostId() {
        Seat host = null;

        for (Seat seat : this.seats) {
            if (!seat.bot && (host == null || seat.joined < host.joined)) host = seat;
        }

        return host == null ? 0 : host.userId;
    }

    public boolean hasPlayers() {
        return this.hostId() != 0;
    }

    public String top() {
        return this.pile.isEmpty() ? "" : this.pile.get(this.pile.size() - 1);
    }

    public int id() {
        return this.id;
    }

    public UnoRules rules() {
        return this.rules;
    }

    public State state() {
        return this.state;
    }

    public List<Seat> seats() {
        return this.seats;
    }

    public char colour() {
        return this.colour;
    }

    public int turn() {
        return this.turn;
    }

    public int direction() {
        return this.direction;
    }

    public long turnEndsAt() {
        return this.turnEndsAt;
    }

    public boolean drew() {
        return this.drew;
    }

    public String drawnCard() {
        return this.drawnCard;
    }

    public int pendingDraw() {
        return this.pendingDraw;
    }

    public int deckSize() {
        return this.deck.size();
    }

    public int winnerId() {
        return this.winnerId;
    }

    public String winnerName() {
        return this.winnerName;
    }

    public List<Event> log() {
        return new ArrayList<>(this.log);
    }

    public long version() {
        return this.version;
    }

    /** For the tests: puts these cards in a player's hand and this card on the pile. */
    void arrange(int userId, List<String> hand, String top, int turnIndex) {
        Seat seat = this.seat(userId);

        seat.hand.clear();
        seat.hand.addAll(hand);
        this.pile.add(top);
        this.colour = top.charAt(0);
        this.turn = turnIndex;
        this.drew = false;
    }

    /** For the tests: how many cards each player holds. */
    Map<Integer, Integer> handSizes() {
        Map<Integer, Integer> sizes = new HashMap<>();

        for (Seat seat : this.seats) sizes.put(seat.userId, seat.hand.size());

        return sizes;
    }
}
