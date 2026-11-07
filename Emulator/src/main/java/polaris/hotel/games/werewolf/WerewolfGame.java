package polaris.hotel.games.werewolf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * One village: twelve seats round the fire, the players and robots sitting on them, their roles, and the game itself,
 * night by night (Cupid, the guard, the seer, the werewolves, the witch), then the day (the deaths, the debate, the
 * vote) until a camp wins. Every action takes the time in milliseconds, so the rules run the same in the tests.
 * Not thread-safe: the manager locks the village.
 */
public final class WerewolfGame {

    public enum State {
        WAITING,
        PLAYING,
        FINISHED
    }

    public enum Step {
        NONE,
        CUPID,
        GUARD,
        SEER,
        WOLVES,
        WITCH,
        DAWN,
        DEBATE,
        VOTE,
        HUNTER;

        public String code() {
            return this.name().toLowerCase();
        }
    }

    /** Who may read an event. */
    public enum Channel {
        ALL,
        WOLVES,
        DEAD,
        LOVERS,
        PRIVATE
    }

    /** A player of the village; a robot has a negative id. */
    public static final class Seat {
        final int userId;
        final String name;
        final String figure;
        final String gender;
        final boolean bot;
        int chair;
        WerewolfRole role = WerewolfRole.VILLAGER;
        boolean alive = true;
        int lover;
        /** This step's choice (and Cupid's second one). */
        int choice;

        int second;
        boolean done;
        boolean ready;
        long actAt;
        long lastSeen;
        /** The order in which the players came: the first one still here is the host. */
        long joined;
        /** What the seer has learnt (seer robots vote on it). */
        final Map<Integer, WerewolfRole> seen = new HashMap<>();

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

        public WerewolfRole role() {
            return this.role;
        }

        public boolean alive() {
            return this.alive;
        }
    }

    /** Something that happened: its number, its kind, who may read it (and to whom, for a private one), and its words. */
    public record Event(long seq, String type, Channel channel, int to, String a, String b, String c) {}

    private static final int LOG_SIZE = 160;
    private static final long DAWN_MS = 7000L;
    private static final long PAUSE_MS = 1800L;
    private static final String[] BOT_NAMES = {
        "Bobby",
        "Marcel",
        "Lulu",
        "Hector",
        "Firmin",
        "Jojo",
        "Pépette",
        "Germaine",
        "Raymond",
        "Ginette",
        "Léon",
        "Mimi"
    };
    private static final String[] BOT_FIGURES = {
        "hr-828-45.hd-180-1.ch-3215-92.lg-3023-110.sh-305-62",
        "hr-515-33.hd-600-1.ch-635-73.lg-716-82.sh-735-68",
        "hr-893-48.hd-180-2.ch-3030-81.lg-275-82.sh-290-91",
        "hr-545-61.hd-600-10.ch-665-71.lg-696-64.sh-725-62",
        "hr-170-42.hd-190-8.ch-215-66.lg-280-110.sh-300-64",
        "hr-831-61.hd-180-1.ch-255-82.lg-280-82.sh-290-62",
        "hr-890-45.hd-600-1.ch-665-92.lg-720-82.sh-907-62",
        "hr-515-48.hd-600-2.ch-660-81.lg-716-110.sh-735-91",
        "hr-125-61.hd-180-8.ch-215-110.lg-285-82.sh-300-62",
        "hr-545-33.hd-600-10.ch-635-66.lg-696-82.sh-725-64",
        "hr-165-45.hd-190-1.ch-3030-73.lg-275-110.sh-290-62",
        "hr-893-61.hd-600-2.ch-665-71.lg-716-64.sh-735-62"
    };
    private static final String[] BOT_GENDERS = {"M", "M", "F", "M", "M", "M", "F", "F", "M", "F", "M", "F"};

    private final int id;
    private final Random random;
    private final List<Seat> seats = new ArrayList<>();
    private final Deque<Event> log = new ArrayDeque<>();
    private final Deque<Step> plan = new ArrayDeque<>();
    private final Deque<Seat> hunters = new ArrayDeque<>();
    private WerewolfRules rules;
    private State state = State.WAITING;
    private Step step = Step.NONE;
    private Step afterHunters = Step.NONE;
    private long stepEndsAt;
    private long joins;
    private int night;
    private int wolfVictim;
    private int protectedId;
    private int lastProtectedId;
    private int poisoned;
    private boolean witchSaved;
    private boolean lifePotion = true;
    private boolean deathPotion = true;
    private String winner = "";
    private long seq;
    private long nextBotLineAt;
    private int nextBotId = -1;
    private int startingPlayers;

    public WerewolfGame(int id, WerewolfRules rules, Random random) {
        this.id = id;
        this.rules = rules;
        this.random = random;
    }

    // ---- Seats

    public void join(int userId, String name, String figure, String gender, int chair, long now) {
        if (this.state == State.PLAYING) throw new WerewolfException("already_started");
        if (this.seat(userId) != null) return;
        if (this.seats.size() >= this.rules.maxPlayers()) throw new WerewolfException("table_full");

        Seat seat = new Seat(userId, name, figure, gender, false, this.freeChair(chair), now);

        seat.joined = ++this.joins;
        this.seats.add(seat);
        this.sortSeats();
        this.event("join", Channel.ALL, 0, name, "", "");
    }

    public void sit(int userId, int chair) {
        Seat seat = this.seat(userId);

        if (seat == null) throw new WerewolfException("no_table");
        if (this.state == State.PLAYING) throw new WerewolfException("already_started");
        if (chair < 0 || chair >= this.rules.maxPlayers() || this.chairTaken(chair))
            throw new WerewolfException("chair_taken");

        seat.chair = chair;
        this.sortSeats();
    }

    public void addBot(int hostId, long now) {
        this.requireHost(hostId);
        if (!this.rules.bots()) throw new WerewolfException("no_bots");
        if (this.state == State.PLAYING) throw new WerewolfException("already_started");
        if (this.seats.size() >= this.rules.maxPlayers()) throw new WerewolfException("table_full");

        String name = null;
        int index = 0;

        for (int candidate = 0; candidate < BOT_NAMES.length; candidate++) {
            if (this.byName(BOT_NAMES[candidate]) == null) {
                name = BOT_NAMES[candidate];
                index = candidate;
                break;
            }
        }

        if (name == null) throw new WerewolfException("table_full");

        this.seats.add(new Seat(
                this.nextBotId--, name, BOT_FIGURES[index], BOT_GENDERS[index], true, this.freeChair(-1), now));
        this.sortSeats();
        this.event("join", Channel.ALL, 0, name, "", "");
    }

    /** Fills every free seat with robots. */
    public void fillWithBots(int hostId, long now) {
        this.requireHost(hostId);

        while (this.seats.size() < this.rules.maxPlayers()) this.addBot(hostId, now);
    }

    public void removeBot(int hostId, int botId) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new WerewolfException("already_started");

        Seat bot = this.seat(botId);

        if (bot == null || !bot.bot) throw new WerewolfException("no_bot");

        this.seats.remove(bot);
        this.event("leave", Channel.ALL, 0, bot.name, "", "");
    }

    public void setRules(int hostId, WerewolfRules next) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new WerewolfException("already_started");

        this.rules = next;

        if (!next.bots()) this.seats.removeIf(seat -> seat.bot);

        while (this.seats.size() > next.maxPlayers()) {
            Seat last = null;

            for (Seat seat : this.seats) {
                if (seat.bot) last = seat;
            }

            this.seats.remove(last != null ? last : this.seats.get(this.seats.size() - 1));
        }

        // As many stumps as players: whoever sat on a stump that went away takes a free one.
        for (Seat seat : this.seats) {
            if (seat.chair >= next.maxPlayers()) {
                seat.chair = -1;
                seat.chair = this.freeChair(-1);
            }
        }

        this.sortSeats();
        this.event("rules", Channel.ALL, 0, "", "", "");
    }

    /** A player leaves: during a game they die (their role is shown), and the game may end. */
    public void leave(int userId, long now) {
        Seat seat = this.seat(userId);

        if (seat == null) return;

        if (this.state == State.PLAYING && seat.alive) {
            seat.alive = false;
            this.event("death", Channel.ALL, 0, seat.name, seat.role.code(), "left");
            this.killLover(seat);
        }

        this.seats.remove(seat);

        if (this.state != State.PLAYING) {
            this.event("leave", Channel.ALL, 0, seat.name, "", "");
            return;
        }

        this.event("leave", Channel.ALL, 0, seat.name, "", "");

        if (!this.checkWin()) this.stepDoneCheck(now);
    }

    public void seen(int userId, long now) {
        Seat seat = this.seat(userId);

        if (seat != null) seat.lastSeen = now;
    }

    // ---- The game

    public void start(int hostId, long now) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new WerewolfException("already_started");
        if (this.seats.size() < WerewolfRules.MIN_PLAYERS) throw new WerewolfException("not_enough_players");

        List<WerewolfRole> roles = this.rules.composition(this.seats.size());

        Collections.shuffle(roles, this.random);
        this.log.clear();
        this.plan.clear();
        this.hunters.clear();
        this.winner = "";
        this.night = 0;
        this.lifePotion = true;
        this.deathPotion = true;
        this.lastProtectedId = 0;
        this.startingPlayers = this.seats.size();

        for (int index = 0; index < this.seats.size(); index++) {
            Seat seat = this.seats.get(index);

            seat.role = roles.get(index);
            seat.alive = true;
            seat.lover = 0;
            seat.ready = false;
            seat.seen.clear();
        }

        this.state = State.PLAYING;
        this.event("welcome", Channel.ALL, 0, composition(roles), "", "");

        for (Seat seat : this.seats) this.event("your_role", Channel.PRIVATE, seat.userId, seat.role.code(), "", "");

        for (Seat wolf : this.seats) {
            if (wolf.role.wolf()) this.event("pack", Channel.PRIVATE, wolf.userId, this.wolfNames(), "", "");
        }

        this.beginNight(now);
    }

    /**
     * The player's action for the step: Cupid's two lovers, the guard's protégé, the seer's look, a werewolf's prey,
     * the vote of the day, or the hunter's shot.
     */
    public void act(int userId, int target, int second, long now) {
        Seat seat = this.actor(userId);

        switch (this.step) {
            case CUPID -> {
                Seat first = this.living(target);
                Seat other = this.living(second);

                if (first == null || other == null || first == other) throw new WerewolfException("pick_two");

                seat.choice = target;
                seat.second = second;
            }
            case GUARD -> {
                if (this.living(target) == null) throw new WerewolfException("bad_target");
                if (target == this.lastProtectedId) throw new WerewolfException("same_protection");

                seat.choice = target;
            }
            case SEER -> {
                Seat looked = this.living(target);

                if (looked == null || looked == seat) throw new WerewolfException("bad_target");
                if (seat.done) throw new WerewolfException("already_done");

                seat.choice = target;
                seat.seen.put(target, looked.role);
                this.event("seer_sees", Channel.PRIVATE, seat.userId, looked.name, looked.role.code(), "");
            }
            case WOLVES -> {
                Seat prey = this.living(target);

                if (prey == null || prey.role.wolf()) throw new WerewolfException("bad_target");

                seat.choice = target;
                this.event("wolf_choice", Channel.WOLVES, 0, seat.name, prey.name, "");
            }
            case VOTE -> {
                Seat accused = this.living(target);

                if (accused == null || accused == seat) throw new WerewolfException("bad_target");

                seat.choice = target;
                this.event("vote", Channel.ALL, 0, seat.name, accused.name, "");
            }
            case HUNTER -> {
                Seat shot = this.living(target);

                if (shot == null || shot == seat) throw new WerewolfException("bad_target");

                seat.choice = target;
            }
            default -> throw new WerewolfException("nothing_to_do");
        }

        seat.done = true;
        this.stepDoneCheck(now);
    }

    /** The witch's night: save the werewolves' prey, poison someone, both, or neither. */
    public void witch(int userId, boolean save, int poison, long now) {
        Seat seat = this.actor(userId);

        if (this.step != Step.WITCH) throw new WerewolfException("nothing_to_do");
        if (save && (!this.lifePotion || this.wolfVictim == 0)) throw new WerewolfException("no_potion");
        if (poison != 0 && (!this.deathPotion || this.living(poison) == null || poison == userId)) {
            throw new WerewolfException("no_potion");
        }

        this.witchSaved = save;
        this.poisoned = poison;

        if (save) this.lifePotion = false;
        if (poison != 0) this.deathPotion = false;

        seat.done = true;
        this.stepDoneCheck(now);
    }

    /** Ready to vote: the debate ends early once every living player is. */
    public void ready(int userId, long now) {
        Seat seat = this.seat(userId);

        if (seat == null || !seat.alive || this.step != Step.DEBATE) throw new WerewolfException("nothing_to_do");

        seat.ready = true;
        this.stepDoneCheck(now);
    }

    /** A message in the chat: the village by day, the pack by night, the dead among themselves. */
    public void say(int userId, String text) {
        Seat seat = this.seat(userId);

        if (seat == null) throw new WerewolfException("no_table");

        Channel channel = this.chatChannel(seat);

        if (channel == null) throw new WerewolfException("village_sleeps");

        this.event("chat", channel, 0, seat.name, text, "");
    }

    /** Where this player's words go now; null when they must stay silent (the village sleeps). */
    public Channel chatChannel(Seat seat) {
        if (this.state != State.PLAYING) return Channel.ALL;
        if (!seat.alive) return Channel.DEAD;
        if (this.isNight()) return seat.role.wolf() ? Channel.WOLVES : null;

        return Channel.ALL;
    }

    public boolean isNight() {
        return this.state == State.PLAYING
                && (this.step == Step.CUPID
                        || this.step == Step.GUARD
                        || this.step == Step.SEER
                        || this.step == Step.WOLVES
                        || this.step == Step.WITCH);
    }

    // ---- The clock

    /** Robots act, a step whose time is over ends. Answers nothing: the players gone are dealt with by the manager. */
    public void tick(long now) {
        if (this.state != State.PLAYING) return;

        for (Seat seat : new ArrayList<>(this.seats)) {
            if (this.state != State.PLAYING) return;
            if (seat.bot && this.isActor(seat) && !seat.done && now >= seat.actAt) this.botActs(seat, now);
        }

        if (this.state == State.PLAYING && this.step == Step.DEBATE && now >= this.nextBotLineAt) this.botTalks(now);

        if (this.state == State.PLAYING && now >= this.stepEndsAt) this.endStep(now);
    }

    // ---- Steps

    private void beginNight(long now) {
        this.night++;
        this.wolfVictim = 0;
        this.poisoned = 0;
        this.witchSaved = false;
        this.protectedId = 0;
        this.plan.clear();

        if (this.night == 1 && this.inGame(WerewolfRole.CUPID)) this.plan.add(Step.CUPID);
        if (this.inGame(WerewolfRole.GUARD)) this.plan.add(Step.GUARD);
        if (this.inGame(WerewolfRole.SEER)) this.plan.add(Step.SEER);

        this.plan.add(Step.WOLVES);

        if (this.inGame(WerewolfRole.WITCH)) this.plan.add(Step.WITCH);

        this.plan.add(Step.DAWN);
        this.event("night", Channel.ALL, 0, String.valueOf(this.night), "", "");
        this.nextStep(now);
    }

    private void nextStep(long now) {
        Step next = this.plan.isEmpty() ? Step.NONE : this.plan.pollFirst();

        if (next == Step.NONE) return;

        this.startStep(next, now);
    }

    private void startStep(Step next, long now) {
        this.step = next;

        for (Seat seat : this.seats) {
            seat.choice = 0;
            seat.second = 0;
            seat.done = false;
            seat.ready = false;
            seat.actAt = now + 2500L + this.random.nextInt(4500);
        }

        long duration =
                switch (next) {
                    case DAWN -> DAWN_MS;
                    case DEBATE -> this.rules.debateSeconds() * 1000L;
                    case VOTE -> this.rules.voteSeconds() * 1000L;
                    case HUNTER -> this.rules.hunterSeconds() * 1000L;
                    default -> this.rules.nightSeconds() * 1000L;
                };

        // A night role whose player is dead still "wakes up", briefly, so nobody learns who is dead from the pace.
        if (this.isNight() && this.actors().isEmpty()) duration = 4000L + this.random.nextInt(3500);

        this.stepEndsAt = now + duration;

        // Robots vote in the middle of the vote, so the players have their say first.
        if (next == Step.VOTE) {
            for (Seat seat : this.seats)
                seat.actAt = now + duration / 3 + this.random.nextInt((int) (duration / 3) + 1);
        }

        switch (next) {
            case WITCH -> {
                Seat prey = this.seat(this.wolfVictim);

                for (Seat witch : this.actors()) {
                    this.event("witch_sees", Channel.PRIVATE, witch.userId, prey == null ? "" : prey.name, "", "");
                }
            }
            case DAWN -> this.dawn(now);
            case DEBATE -> this.nextBotLineAt = now + 4000L + this.random.nextInt(5000);
            case HUNTER -> {
                Seat hunter = this.hunters.peekFirst();

                if (hunter != null) this.event("hunter_wakes", Channel.ALL, 0, hunter.name, "", "");
            }
            default -> {}
        }

        if (next != Step.DAWN && next != Step.HUNTER) this.event("step", Channel.ALL, 0, next.code(), "", "");
    }

    /** Once every actor has acted, the step ends after a short pause. */
    private void stepDoneCheck(long now) {
        if (this.state != State.PLAYING) return;

        List<Seat> actors = this.actors();

        if (actors.isEmpty()) return;

        boolean all = true;

        for (Seat seat : actors) {
            if (this.step == Step.DEBATE ? !seat.ready : !seat.done) all = false;
        }

        if (all) this.stepEndsAt = Math.min(this.stepEndsAt, now + PAUSE_MS);
    }

    private void endStep(long now) {
        switch (this.step) {
            case CUPID -> this.endCupid();
            case GUARD -> {
                Seat guard = this.firstActor();

                if (guard != null && guard.choice != 0) {
                    this.protectedId = guard.choice;
                    this.lastProtectedId = guard.choice;
                } else {
                    this.lastProtectedId = 0;
                }
            }
            case WOLVES -> this.wolfVictim = this.wolvesPrey();
            case DAWN -> {
                if (this.hunterNext(Step.DEBATE, now)) return;
                if (this.checkWin()) return;

                this.startStep(Step.DEBATE, now);
                return;
            }
            case DEBATE -> {
                this.startStep(Step.VOTE, now);
                return;
            }
            case VOTE -> {
                this.endVote();

                if (this.hunterNext(Step.NONE, now)) return;
                if (this.checkWin()) return;

                this.beginNight(now);
                return;
            }
            case HUNTER -> {
                this.endHunter();

                if (this.hunterNext(this.afterHunters, now)) return;
                if (this.checkWin()) return;

                if (this.afterHunters == Step.NONE) this.beginNight(now);
                else this.startStep(this.afterHunters, now);
                return;
            }
            default -> {}
        }

        this.nextStep(now);
    }

    private void endCupid() {
        Seat cupid = this.firstActor();
        List<Seat> alive = this.livingSeats();

        if (cupid == null || alive.size() < 2) return;

        Seat first = this.seat(cupid.choice);
        Seat other = this.seat(cupid.second);

        if (first == null || other == null || first == other) {
            Collections.shuffle(alive, this.random);
            first = alive.get(0);
            other = alive.get(1);
        }

        first.lover = other.userId;
        other.lover = first.userId;
        this.event("lovers", Channel.PRIVATE, first.userId, other.name, other.role.code(), "");
        this.event("lovers", Channel.PRIVATE, other.userId, first.name, first.role.code(), "");
        this.event("cupid_done", Channel.PRIVATE, cupid.userId, first.name, other.name, "");
    }

    private int wolvesPrey() {
        Map<Integer, Integer> votes = new HashMap<>();

        for (Seat wolf : this.actors()) {
            if (wolf.choice != 0) votes.merge(wolf.choice, 1, Integer::sum);
        }

        if (votes.isEmpty()) {
            List<Seat> prey = new ArrayList<>();

            for (Seat seat : this.livingSeats()) {
                if (!seat.role.wolf()) prey.add(seat);
            }

            return prey.isEmpty() ? 0 : prey.get(this.random.nextInt(prey.size())).userId;
        }

        return this.mostVoted(votes, true);
    }

    /** The morning: the werewolves' prey dies unless guarded or saved, the poisoned one dies, lovers follow. */
    private void dawn(long now) {
        List<Seat> dead = new ArrayList<>();
        Seat prey = this.seat(this.wolfVictim);

        if (prey != null && prey.alive && !this.witchSaved && prey.userId != this.protectedId) dead.add(prey);

        Seat poisonedSeat = this.seat(this.poisoned);

        if (poisonedSeat != null && poisonedSeat.alive && !dead.contains(poisonedSeat)) dead.add(poisonedSeat);

        this.event("day", Channel.ALL, 0, String.valueOf(this.night), "", "");

        if (dead.isEmpty()) this.event("nobody_died", Channel.ALL, 0, "", "", "");

        for (Seat seat : dead) this.kill(seat, seat == prey ? "wolves" : "poison");
    }

    private void endVote() {
        Map<Integer, Integer> votes = new HashMap<>();

        for (Seat seat : this.livingSeats()) {
            if (seat.choice != 0) votes.merge(seat.choice, 1, Integer::sum);
        }

        int accused = this.mostVoted(votes, false);
        Seat seat = this.seat(accused);

        if (seat == null) {
            this.event("vote_tie", Channel.ALL, 0, "", "", "");
            return;
        }

        this.kill(seat, "vote");
    }

    private void endHunter() {
        Seat hunter = this.hunters.pollFirst();

        if (hunter == null) return;

        Seat shot = this.seat(hunter.choice);

        if (shot == null || !shot.alive) {
            List<Seat> alive = this.livingSeats();

            shot = alive.isEmpty() ? null : alive.get(this.random.nextInt(alive.size()));
        }

        if (shot == null) return;

        this.event("hunter_shoots", Channel.ALL, 0, hunter.name, shot.name, "");
        this.kill(shot, "hunter");
    }

    /** Starts the shot of a hunter who died, then goes on to `then`; false when no hunter waits. */
    private boolean hunterNext(Step then, long now) {
        if (this.hunters.isEmpty()) return false;

        this.afterHunters = then;
        this.startStep(Step.HUNTER, now);
        return true;
    }

    private void kill(Seat seat, String cause) {
        if (!seat.alive) return;

        seat.alive = false;
        this.event("death", Channel.ALL, 0, seat.name, this.rules.revealRoles() ? seat.role.code() : "", cause);

        if (seat.role == WerewolfRole.HUNTER) this.hunters.addLast(seat);

        this.killLover(seat);
    }

    private void killLover(Seat seat) {
        Seat lover = this.seat(seat.lover);

        if (lover != null && lover.alive) this.kill(lover, "grief");
    }

    /** The plurality of the votes; a tie draws at random for the werewolves, and leaves nobody for the village. */
    private int mostVoted(Map<Integer, Integer> votes, boolean drawOnTie) {
        int best = 0;
        List<Integer> top = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                top.clear();
            }

            if (entry.getValue() == best) top.add(entry.getKey());
        }

        if (top.isEmpty()) return 0;
        if (top.size() == 1) return top.get(0);

        return drawOnTie ? top.get(this.random.nextInt(top.size())) : 0;
    }

    /** Ends the game when a camp has won: the lovers alone, the village (no wolf left), or the werewolves (only wolves). */
    private boolean checkWin() {
        List<Seat> alive = this.livingSeats();
        int wolves = 0;

        for (Seat seat : alive) {
            if (seat.role.wolf()) wolves++;
        }

        String camp = null;

        if (alive.size() == 2
                && alive.get(0).lover == alive.get(1).userId
                && alive.get(0).role.wolf() != alive.get(1).role.wolf()) {
            camp = "lovers";
        } else if (alive.isEmpty()) {
            camp = "nobody";
        } else if (wolves == 0) {
            camp = "village";
        } else if (wolves == alive.size()) {
            camp = "wolves";
        }

        if (camp == null) return false;

        this.state = State.FINISHED;
        this.step = Step.NONE;
        this.winner = camp;
        this.event("win", Channel.ALL, 0, camp, "", "");

        for (Seat seat : this.seats) this.event("reveal", Channel.ALL, 0, seat.name, seat.role.code(), "");

        return true;
    }

    // ---- Robots

    private void botActs(Seat bot, long now) {
        try {
            switch (this.step) {
                case CUPID -> {
                    List<Seat> alive = this.livingSeats();

                    Collections.shuffle(alive, this.random);
                    this.act(bot.userId, alive.get(0).userId, alive.get(1).userId, now);
                }
                case GUARD -> {
                    Seat target = this.pick(this.livingSeats(), seat -> seat.userId != this.lastProtectedId);

                    if (target != null) this.act(bot.userId, target.userId, 0, now);
                }
                case SEER -> {
                    Seat target =
                            this.pick(this.livingSeats(), seat -> seat != bot && !bot.seen.containsKey(seat.userId));

                    if (target == null) target = this.pick(this.livingSeats(), seat -> seat != bot);
                    if (target != null) this.act(bot.userId, target.userId, 0, now);
                }
                case WOLVES -> {
                    int follow = 0;

                    for (Seat wolf : this.actors()) {
                        if (wolf != bot && wolf.choice != 0) follow = wolf.choice;
                    }

                    if (follow == 0) {
                        Seat target = this.pick(this.livingSeats(), seat -> !seat.role.wolf());

                        if (target != null) follow = target.userId;
                    }

                    if (follow != 0) this.act(bot.userId, follow, 0, now);
                }
                case WITCH -> {
                    boolean save = this.lifePotion && this.wolfVictim != 0 && this.wolfVictim != bot.userId
                            ? this.random.nextInt(100) < 45
                            : this.lifePotion && this.wolfVictim == bot.userId;
                    int poison = 0;

                    if (this.deathPotion && this.night >= 2 && this.random.nextInt(100) < 20) {
                        Seat target = this.pick(this.livingSeats(), seat -> seat != bot);

                        if (target != null) poison = target.userId;
                    }

                    this.witch(bot.userId, save, poison, now);
                }
                case VOTE -> this.botVotes(bot, now);
                case HUNTER -> {
                    Seat target = this.pick(
                            this.livingSeats(), seat -> seat != bot && !(bot.role.wolf() && seat.role.wolf()));

                    if (target != null) this.act(bot.userId, target.userId, 0, now);
                }
                case DEBATE -> this.ready(bot.userId, now);
                default -> {}
            }
        } catch (WerewolfException e) {
            bot.done = true;
        }
    }

    /**
     * A robot votes: a werewolf never against the pack, a seer against a wolf it found, the others with the crowd (two
     * times in five) or at random.
     */
    private void botVotes(Seat bot, long now) {
        for (Map.Entry<Integer, WerewolfRole> entry : bot.seen.entrySet()) {
            Seat known = this.living(entry.getKey());

            if (known != null && entry.getValue().wolf()) {
                this.act(bot.userId, known.userId, 0, now);
                return;
            }
        }

        Map<Integer, Integer> votes = new HashMap<>();

        for (Seat seat : this.livingSeats()) {
            if (seat.choice != 0) votes.merge(seat.choice, 1, Integer::sum);
        }

        int crowd = this.mostVoted(votes, true);
        Seat target = this.living(crowd);

        if (target == null
                || target == bot
                || (bot.role.wolf() && target.role.wolf())
                || bot.lover == target.userId
                || this.random.nextInt(100) < 60) {
            target = this.pick(
                    this.livingSeats(),
                    seat -> seat != bot && seat.userId != bot.lover && !(bot.role.wolf() && seat.role.wolf()));
        }

        if (target != null) this.act(bot.userId, target.userId, 0, now);
    }

    /** A robot says something during the debate, now and then. */
    private void botTalks(long now) {
        this.nextBotLineAt = now + 7000L + this.random.nextInt(9000);

        List<Seat> bots = new ArrayList<>();

        for (Seat seat : this.livingSeats()) {
            if (seat.bot) bots.add(seat);
        }

        if (bots.isEmpty()) return;

        Seat bot = bots.get(this.random.nextInt(bots.size()));
        Seat other = this.pick(this.livingSeats(), seat -> seat != bot && !(bot.role.wolf() && seat.role.wolf()));

        this.event(
                "bot_line",
                Channel.ALL,
                0,
                bot.name,
                String.valueOf(this.random.nextInt(12)),
                other == null ? "" : other.name);
    }

    private Seat pick(List<Seat> seats, java.util.function.Predicate<Seat> filter) {
        List<Seat> options = new ArrayList<>();

        for (Seat seat : seats) {
            if (filter.test(seat)) options.add(seat);
        }

        return options.isEmpty() ? null : options.get(this.random.nextInt(options.size()));
    }

    // ---- Who acts

    /** The players who act in this step (the dead act nowhere, the hunter only in his own step). */
    public List<Seat> actors() {
        List<Seat> actors = new ArrayList<>();

        if (this.step == Step.HUNTER) {
            Seat hunter = this.hunters.peekFirst();

            if (hunter != null) actors.add(hunter);

            return actors;
        }

        for (Seat seat : this.seats) {
            if (seat.alive && this.acts(seat)) actors.add(seat);
        }

        return actors;
    }

    private boolean acts(Seat seat) {
        return switch (this.step) {
            case CUPID -> seat.role == WerewolfRole.CUPID;
            case GUARD -> seat.role == WerewolfRole.GUARD;
            case SEER -> seat.role == WerewolfRole.SEER;
            case WOLVES -> seat.role.wolf();
            case WITCH -> seat.role == WerewolfRole.WITCH;
            case DEBATE, VOTE -> true;
            default -> false;
        };
    }

    public boolean isActor(Seat seat) {
        return this.actors().contains(seat);
    }

    private Seat actor(int userId) {
        if (this.state != State.PLAYING) throw new WerewolfException("not_playing");

        Seat seat = this.seat(userId);

        if (seat == null || !this.isActor(seat)) throw new WerewolfException("not_your_turn");

        return seat;
    }

    private Seat firstActor() {
        List<Seat> actors = this.actors();

        return actors.isEmpty() ? null : actors.get(0);
    }

    private boolean inGame(WerewolfRole role) {
        for (Seat seat : this.seats) {
            if (seat.role == role) return true;
        }

        return false;
    }

    // ---- Helpers

    private void event(String type, Channel channel, int to, String a, String b, String c) {
        this.log.addLast(new Event(++this.seq, type, channel, to, a, b, c));

        while (this.log.size() > LOG_SIZE) this.log.pollFirst();
    }

    private static String composition(List<WerewolfRole> roles) {
        Map<WerewolfRole, Integer> counts = new HashMap<>();

        for (WerewolfRole role : roles) counts.merge(role, 1, Integer::sum);

        StringBuilder text = new StringBuilder();

        for (WerewolfRole role : WerewolfRole.values()) {
            Integer count = counts.get(role);

            if (count == null) continue;
            if (!text.isEmpty()) text.append(',');

            text.append(role.code()).append(':').append(count);
        }

        return text.toString();
    }

    private String wolfNames() {
        StringBuilder names = new StringBuilder();

        for (Seat seat : this.seats) {
            if (!seat.role.wolf()) continue;
            if (!names.isEmpty()) names.append(", ");

            names.append(seat.name);
        }

        return names.toString();
    }

    /** A free stump: the one wanted, or the first free one; there are as many stumps as players. */
    private int freeChair(int wanted) {
        int chairs = this.rules.maxPlayers();

        if (wanted >= 0 && wanted < chairs && !this.chairTaken(wanted)) return wanted;

        for (int chair = 0; chair < chairs; chair++) {
            if (!this.chairTaken(chair)) return chair;
        }

        throw new WerewolfException("table_full");
    }

    private boolean chairTaken(int chair) {
        for (Seat seat : this.seats) {
            if (seat.chair == chair) return true;
        }

        return false;
    }

    private void sortSeats() {
        this.seats.sort(Comparator.comparingInt(seat -> seat.chair));
    }

    private void requireHost(int userId) {
        if (this.hostId() != userId) throw new WerewolfException("not_host");
    }

    public Seat seat(int userId) {
        if (userId == 0) return null;

        for (Seat seat : this.seats) {
            if (seat.userId == userId) return seat;
        }

        return null;
    }

    private Seat byName(String name) {
        for (Seat seat : this.seats) {
            if (seat.name.equals(name)) return seat;
        }

        return null;
    }

    private Seat living(int userId) {
        Seat seat = this.seat(userId);

        return seat != null && seat.alive ? seat : null;
    }

    public List<Seat> livingSeats() {
        List<Seat> alive = new ArrayList<>();

        for (Seat seat : this.seats) {
            if (seat.alive) alive.add(seat);
        }

        return alive;
    }

    /** The host: the player who came first among those still here (changing stumps does not change the host). */
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

    /** Who may read this event. */
    public boolean visible(Event event, Seat reader) {
        if (reader == null) return event.channel() == Channel.ALL;

        return switch (event.channel()) {
            case ALL -> true;
            case WOLVES -> reader.role.wolf() || this.state == State.FINISHED;
            case DEAD -> !reader.alive || this.state == State.FINISHED;
            case LOVERS -> reader.lover != 0;
            case PRIVATE -> event.to() == reader.userId;
        };
    }

    /** The wolves' current prey choices, for the pack: prey id to the names of the wolves on it. */
    public Map<Integer, Set<String>> wolfChoices() {
        Map<Integer, Set<String>> choices = new HashMap<>();

        if (this.step != Step.WOLVES) return choices;

        for (Seat wolf : this.actors()) {
            if (wolf.choice != 0)
                choices.computeIfAbsent(wolf.choice, key -> new LinkedHashSet<>())
                        .add(wolf.name);
        }

        return choices;
    }

    /** The day's votes so far: accused id to the number of votes. */
    public Map<Integer, Integer> votes() {
        Map<Integer, Integer> votes = new HashMap<>();

        if (this.step != Step.VOTE) return votes;

        for (Seat seat : this.livingSeats()) {
            if (seat.choice != 0) votes.merge(seat.choice, 1, Integer::sum);
        }

        return votes;
    }

    public int id() {
        return this.id;
    }

    public WerewolfRules rules() {
        return this.rules;
    }

    public State state() {
        return this.state;
    }

    public Step step() {
        return this.step;
    }

    public long stepEndsAt() {
        return this.stepEndsAt;
    }

    public int night() {
        return this.night;
    }

    public List<Seat> seats() {
        return this.seats;
    }

    public int wolfVictim() {
        return this.wolfVictim;
    }

    public int lastProtectedId() {
        return this.lastProtectedId;
    }

    public boolean lifePotion() {
        return this.lifePotion;
    }

    public boolean deathPotion() {
        return this.deathPotion;
    }

    public String winner() {
        return this.winner;
    }

    public int startingPlayers() {
        return this.startingPlayers;
    }

    public List<Event> log() {
        return new ArrayList<>(this.log);
    }

    /** For the tests: deals these roles in seat order. */
    void deal(List<WerewolfRole> roles) {
        for (int index = 0; index < roles.size() && index < this.seats.size(); index++)
            this.seats.get(index).role = roles.get(index);
    }
}
