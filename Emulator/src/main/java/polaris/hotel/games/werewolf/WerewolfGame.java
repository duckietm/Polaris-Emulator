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
import java.util.function.Predicate;

/**
 * One village: up to twelve seats round the fire, the players and robots sitting on them, their roles, and the game
 * itself, night by night (the first night's choices, the guard, the seer, the fox, the raven, the werewolves and their
 * special wolves, the witch, the piper), then the day (the deaths, the debate, the vote) until a camp wins. Every action
 * takes the time in milliseconds, so the rules run the same in the tests. Not thread-safe: the manager locks the
 * village.
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
        WOLF_DOG,
        WILD_CHILD,
        GUARD,
        SEER,
        FOX,
        RAVEN,
        WOLVES,
        ACCURSED,
        BIG_BAD_WOLF,
        WHITE_WOLF,
        WITCH,
        PIPER,
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
        /** Wakes up with the pack: a werewolf, or a player who joined it (infected, wild child, wolf dog). */
        boolean pack;

        int lover;
        /** This step's choice (and the second one of Cupid and the piper). */
        int choice;

        int second;
        /** This step's yes or no (the wolf dog's side, the accursed father's bite). */
        boolean yes;

        boolean done;
        boolean ready;
        boolean charmed;
        /** False for the village idiot once spared by the vote. */
        boolean canVote = true;
        /** The village idiot spared by the vote: everybody knows his role. */
        boolean revealed;
        /** The elder survives the first attack of the werewolves. */
        int lives = 1;
        /** The wild child's model. */
        int model;

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

        public boolean pack() {
            return this.pack;
        }

        public boolean charmed() {
            return this.charmed;
        }

        public boolean revealed() {
            return this.revealed;
        }
    }

    /** Something that happened: its number, its kind, who may read it (and to whom, for a private one), and its words. */
    public record Event(long seq, String type, Channel channel, int to, String a, String b, String c) {}

    private static final int LOG_SIZE = 200;
    private static final long DAWN_MS = 8000L;
    private static final long PAUSE_MS = 1800L;
    /** A game that lasts this many nights ends with nobody winning. */
    private static final int MAX_NIGHTS = 40;

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
    private final List<Seat> newlyCharmed = new ArrayList<>();
    private WerewolfRules rules;
    private State state = State.WAITING;
    private Step step = Step.NONE;
    private Step afterHunters = Step.NONE;
    private long stepEndsAt;
    private long joins;
    private int night;
    private int wolfVictim;
    private int extraVictim;
    private int whiteVictim;
    private int protectedId;
    private int lastProtectedId;
    private int poisoned;
    private boolean witchSaved;
    private boolean lifePotion = true;
    private boolean deathPotion = true;
    private boolean infectUsed;
    /** A member of the pack died: the big bad wolf hunts no more. */
    private boolean packLost;
    /** The village killed its elder: its special roles lose their powers. */
    private boolean powersLost;

    private boolean foxActive = true;
    private int ravenTarget;
    /** Marked by the raven for today's vote: two votes against them. */
    private int ravenMarked;
    /** The werewolf poisoned by the knight's rusty sword: he dies at the next dawn. */
    private int rustyWolf;

    private boolean angelWon;
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
            if (seat.pack) this.packLost = true;
            this.afterDeath(seat);
        }

        this.seats.remove(seat);
        this.hunters.remove(seat);
        this.event("leave", Channel.ALL, 0, seat.name, "", "");

        if (this.state == State.PLAYING && !this.checkWin()) this.stepDoneCheck(now);
    }

    public void seen(int userId, long now) {
        Seat seat = this.seat(userId);

        if (seat != null) seat.lastSeen = now;
    }

    // ---- The game

    public void start(int hostId, long now) {
        this.startWith(hostId, now, null);
    }

    /** Starts the game; `roles` (for the tests) deals these roles in seat order instead of drawing them. */
    void startWith(int hostId, long now, List<WerewolfRole> roles) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new WerewolfException("already_started");
        if (this.seats.size() < WerewolfRules.MIN_PLAYERS) throw new WerewolfException("not_enough_players");

        List<WerewolfRole> dealt;

        if (roles == null) {
            dealt = this.rules.composition(this.seats.size(), this.random);
            Collections.shuffle(dealt, this.random);
        } else {
            dealt = new ArrayList<>(roles);

            while (dealt.size() < this.seats.size()) dealt.add(WerewolfRole.VILLAGER);
        }

        this.log.clear();
        this.plan.clear();
        this.hunters.clear();
        this.newlyCharmed.clear();
        this.winner = "";
        this.night = 0;
        this.lifePotion = true;
        this.deathPotion = true;
        this.lastProtectedId = 0;
        this.infectUsed = false;
        this.packLost = false;
        this.powersLost = false;
        this.foxActive = true;
        this.ravenTarget = 0;
        this.ravenMarked = 0;
        this.rustyWolf = 0;
        this.angelWon = false;
        this.startingPlayers = this.seats.size();

        for (int index = 0; index < this.seats.size(); index++) {
            Seat seat = this.seats.get(index);

            seat.role = dealt.get(index);
            seat.pack = seat.role.wolf();
            seat.alive = true;
            seat.lover = 0;
            seat.ready = false;
            seat.charmed = false;
            seat.canVote = true;
            seat.revealed = false;
            seat.lives = seat.role == WerewolfRole.ELDER ? 2 : 1;
            seat.model = 0;
            seat.seen.clear();
        }

        this.state = State.PLAYING;
        this.event("welcome", Channel.ALL, 0, composition(dealt.subList(0, this.seats.size())), "", "");

        for (Seat seat : this.seats) this.event("your_role", Channel.PRIVATE, seat.userId, seat.role.code(), "", "");

        for (Seat wolf : this.seats) {
            if (wolf.pack) this.event("pack", Channel.PRIVATE, wolf.userId, this.packNames(), "", "");
        }

        for (Seat sister : this.seats) {
            if (sister.role != WerewolfRole.SISTER) continue;

            for (Seat other : this.seats) {
                if (other != sister && other.role == WerewolfRole.SISTER)
                    this.event("sister", Channel.PRIVATE, sister.userId, other.name, "", "");
            }
        }

        this.beginNight(now);
    }

    /**
     * The player's choice of a player for the step: Cupid's two lovers, the wild child's model, the guard's protégé,
     * the seer's look, the fox's sniff, the raven's mark, a werewolf's prey, the big bad wolf's and the white wolf's
     * own prey, the piper's two charmed, the vote of the day, or the hunter's shot.
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
            case WILD_CHILD -> {
                if (this.living(target) == null || target == userId) throw new WerewolfException("bad_target");

                seat.choice = target;
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
            case FOX -> {
                Seat sniffed = this.living(target);

                if (sniffed == null || sniffed == seat) throw new WerewolfException("bad_target");
                if (seat.done) throw new WerewolfException("already_done");

                seat.choice = target;
                this.sniff(seat, sniffed);
            }
            case RAVEN -> {
                if (this.living(target) == null || target == userId) throw new WerewolfException("bad_target");

                seat.choice = target;
            }
            case WOLVES -> {
                Seat prey = this.living(target);

                if (prey == null || prey.pack) throw new WerewolfException("bad_target");

                seat.choice = target;
                this.event("wolf_choice", Channel.WOLVES, 0, seat.name, prey.name, "");
            }
            case BIG_BAD_WOLF -> {
                Seat prey = this.living(target);

                if (prey == null || prey.pack || target == this.wolfVictim) throw new WerewolfException("bad_target");

                seat.choice = target;
            }
            case WHITE_WOLF -> {
                Seat prey = this.living(target);

                if (prey == null || !prey.pack || prey == seat) throw new WerewolfException("bad_target");

                seat.choice = target;
            }
            case PIPER -> {
                Seat first = this.charmable(target, seat);
                Seat other = second == 0 ? null : this.charmable(second, seat);

                if (first == null || (second != 0 && (other == null || other == first)))
                    throw new WerewolfException("pick_two");
                if (second == 0 && this.charmables(seat).size() > 1) throw new WerewolfException("pick_two");

                seat.choice = target;
                seat.second = second;
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

    /** A yes or no: the wolf dog joins the pack or not, the accursed father bites the prey or lets it be devoured. */
    public void choose(int userId, boolean yes, long now) {
        Seat seat = this.actor(userId);

        if (this.step != Step.WOLF_DOG && this.step != Step.ACCURSED) throw new WerewolfException("nothing_to_do");

        seat.yes = yes;
        seat.done = true;
        this.stepDoneCheck(now);
    }

    /** Lets a step that may be skipped go: the raven marks nobody, the white wolf spares the pack. */
    public void pass(int userId, long now) {
        Seat seat = this.actor(userId);

        if (this.step != Step.RAVEN && this.step != Step.WHITE_WOLF) throw new WerewolfException("nothing_to_do");

        seat.choice = 0;
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
        if (this.isNight()) return seat.pack ? Channel.WOLVES : null;

        return Channel.ALL;
    }

    public boolean isNight() {
        if (this.state != State.PLAYING) return false;

        return switch (this.step) {
            case CUPID,
                    WOLF_DOG,
                    WILD_CHILD,
                    GUARD,
                    SEER,
                    FOX,
                    RAVEN,
                    WOLVES,
                    ACCURSED,
                    BIG_BAD_WOLF,
                    WHITE_WOLF,
                    WITCH,
                    PIPER -> true;
            default -> false;
        };
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
        this.extraVictim = 0;
        this.whiteVictim = 0;
        this.poisoned = 0;
        this.witchSaved = false;
        this.protectedId = 0;
        this.ravenTarget = 0;
        this.newlyCharmed.clear();
        this.plan.clear();

        if (this.night > MAX_NIGHTS) {
            this.finish("nobody");
            return;
        }

        if (this.night == 1) {
            if (this.inGame(WerewolfRole.CUPID)) this.plan.add(Step.CUPID);
            if (this.inGame(WerewolfRole.WOLF_DOG)) this.plan.add(Step.WOLF_DOG);
            if (this.inGame(WerewolfRole.WILD_CHILD)) this.plan.add(Step.WILD_CHILD);
        }

        if (this.inGame(WerewolfRole.GUARD)) this.plan.add(Step.GUARD);
        if (this.inGame(WerewolfRole.SEER)) this.plan.add(Step.SEER);
        if (this.inGame(WerewolfRole.FOX)) this.plan.add(Step.FOX);
        if (this.inGame(WerewolfRole.RAVEN)) this.plan.add(Step.RAVEN);

        this.plan.add(Step.WOLVES);

        if (this.inGame(WerewolfRole.ACCURSED_FATHER) && !this.infectUsed) this.plan.add(Step.ACCURSED);
        if (this.inGame(WerewolfRole.BIG_BAD_WOLF) && !this.packLost) this.plan.add(Step.BIG_BAD_WOLF);
        if (this.inGame(WerewolfRole.WHITE_WOLF) && this.night % 2 == 0) this.plan.add(Step.WHITE_WOLF);
        if (this.inGame(WerewolfRole.WITCH)) this.plan.add(Step.WITCH);
        if (this.inGame(WerewolfRole.PIPER)) this.plan.add(Step.PIPER);

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
            seat.yes = false;
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

        // A night role whose player is dead (or without power) still "wakes up", briefly, so nobody learns from the
        // pace.
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
            case DAWN -> this.dawn();
            case DEBATE -> this.nextBotLineAt = now + 4000L + this.random.nextInt(5000);
            case VOTE -> {
                Seat marked = this.living(this.ravenMarked);

                if (marked != null) this.event("raven", Channel.ALL, 0, marked.name, "", "");
            }
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
            case WOLF_DOG -> this.endWolfDog();
            case WILD_CHILD -> this.endWildChild();
            case GUARD -> {
                Seat guard = this.firstActor();

                if (guard != null && guard.choice != 0) {
                    this.protectedId = guard.choice;
                    this.lastProtectedId = guard.choice;
                } else {
                    this.lastProtectedId = 0;
                }
            }
            case RAVEN -> {
                Seat raven = this.firstActor();

                this.ravenTarget = raven == null ? 0 : raven.choice;
            }
            case WOLVES -> this.wolfVictim = this.wolvesPrey();
            case ACCURSED -> this.endAccursed();
            case BIG_BAD_WOLF -> {
                Seat wolf = this.firstActor();

                if (wolf != null) {
                    Seat prey = this.living(wolf.choice);

                    if (prey == null || prey.pack || prey.userId == this.wolfVictim)
                        prey = this.pick(this.livingSeats(), seat -> !seat.pack && seat.userId != this.wolfVictim);

                    this.extraVictim = prey == null ? 0 : prey.userId;
                }
            }
            case WHITE_WOLF -> {
                Seat wolf = this.firstActor();

                this.whiteVictim = wolf == null ? 0 : wolf.choice;
            }
            case PIPER -> this.endPiper();
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

    private void endWolfDog() {
        Seat dog = this.firstActor();

        if (dog == null) return;

        if (dog.done && dog.yes) {
            dog.pack = true;
            this.event("wolf_dog_wolf", Channel.PRIVATE, dog.userId, "", "", "");
            this.joinPack(dog, "wolf_dog");
        } else {
            this.event("wolf_dog_village", Channel.PRIVATE, dog.userId, "", "", "");
        }
    }

    private void endWildChild() {
        Seat child = this.firstActor();

        if (child == null) return;

        Seat model = this.living(child.choice);

        if (model == null || model == child) model = this.pick(this.livingSeats(), seat -> seat != child);
        if (model == null) return;

        child.model = model.userId;
        this.event("wild_child_model", Channel.PRIVATE, child.userId, model.name, "", "");
    }

    private void endAccursed() {
        Seat father = this.firstActor();
        Seat prey = this.living(this.wolfVictim);

        if (father == null || prey == null || !father.done || !father.yes) return;

        this.infectUsed = true;
        this.wolfVictim = 0;
        prey.pack = true;
        this.event("infected", Channel.PRIVATE, prey.userId, "", "", "");
        this.joinPack(prey, "infected");
    }

    private void endPiper() {
        Seat piper = this.firstActor();

        if (piper == null) return;

        List<Seat> charmed = new ArrayList<>();
        Seat first = this.charmable(piper.choice, piper);
        Seat other = this.charmable(piper.second, piper);

        if (first != null) charmed.add(first);
        if (other != null && other != first) charmed.add(other);

        // The time ran out: the piper's music charms whom it reaches.
        List<Seat> left = this.charmables(piper);

        left.removeAll(charmed);
        Collections.shuffle(left, this.random);

        while (charmed.size() < 2 && !left.isEmpty()) charmed.add(left.remove(0));

        for (Seat seat : charmed) {
            seat.charmed = true;
            this.newlyCharmed.add(seat);
        }
    }

    /** A new member of the pack: the pack learns it (and the newcomer learns the pack). */
    private void joinPack(Seat seat, String why) {
        this.event("pack_joins", Channel.WOLVES, 0, seat.name, why, "");
        this.event("pack", Channel.PRIVATE, seat.userId, this.packNames(), "", "");
    }

    /** The fox sniffs a player and their two living neighbours: a werewolf among them, or he loses his nose. */
    private void sniff(Seat fox, Seat sniffed) {
        List<Seat> group = new ArrayList<>(this.neighbours(sniffed));

        group.add(sniffed);

        boolean wolf = false;

        for (Seat seat : group) {
            if (seat.pack) wolf = true;
        }

        if (wolf) {
            this.event("fox_yes", Channel.PRIVATE, fox.userId, sniffed.name, "", "");
        } else {
            this.foxActive = false;
            this.event("fox_no", Channel.PRIVATE, fox.userId, sniffed.name, "", "");
        }
    }

    private int wolvesPrey() {
        Map<Integer, Integer> votes = new HashMap<>();
        List<Seat> wolves = this.actors();

        // No werewolf left (the piper still plays): nobody is devoured.
        if (wolves.isEmpty()) return 0;

        for (Seat wolf : wolves) {
            if (wolf.choice != 0) votes.merge(wolf.choice, 1, Integer::sum);
        }

        // The pack did not choose in time: it devours somebody anyway.
        if (votes.isEmpty()) {
            Seat prey = this.pick(this.livingSeats(), seat -> !seat.pack);

            return prey == null ? 0 : prey.userId;
        }

        return this.mostVoted(votes, true);
    }

    /**
     * The morning: the attacks of the night (unless guarded; the witch saves the pack's prey; the elder survives one),
     * the poison, the knight's rusty sword, the lovers, then the bear's growl and the piper's charmed wake up.
     */
    private void dawn() {
        List<Seat> dead = new ArrayList<>();
        Map<Seat, String> causes = new HashMap<>();

        this.attack(this.seat(this.wolfVictim), !this.witchSaved, "wolves", dead, causes);
        this.attack(this.seat(this.extraVictim), true, "wolves", dead, causes);

        Seat white = this.seat(this.whiteVictim);

        if (white != null && white.alive && white.userId != this.protectedId && !dead.contains(white)) {
            dead.add(white);
            causes.put(white, "white_wolf");
        }

        Seat poisonedSeat = this.seat(this.poisoned);

        if (poisonedSeat != null && poisonedSeat.alive && !dead.contains(poisonedSeat)) {
            dead.add(poisonedSeat);
            causes.put(poisonedSeat, "poison");
        }

        Seat rusty = this.seat(this.rustyWolf);

        this.rustyWolf = 0;

        if (rusty != null && rusty.alive && !dead.contains(rusty)) {
            dead.add(rusty);
            causes.put(rusty, "rust");
        }

        this.event("day", Channel.ALL, 0, String.valueOf(this.night), "", "");

        if (dead.isEmpty()) this.event("nobody_died", Channel.ALL, 0, "", "", "");

        for (Seat seat : dead) this.kill(seat, causes.get(seat));

        this.ravenMarked = this.living(this.ravenTarget) == null ? 0 : this.ravenTarget;
        this.bear();
        this.charmedWakeUp();
    }

    private void attack(Seat prey, boolean deadly, String cause, List<Seat> dead, Map<Seat, String> causes) {
        if (prey == null || !prey.alive || !deadly || prey.userId == this.protectedId || dead.contains(prey)) return;

        if (prey.role == WerewolfRole.ELDER && prey.lives > 1) {
            prey.lives--;
            this.event("elder_survives", Channel.PRIVATE, prey.userId, "", "", "");
            return;
        }

        dead.add(prey);
        causes.put(prey, cause);
    }

    /** The bear tamer's bear growls when a werewolf sits next to its master. */
    private void bear() {
        for (Seat tamer : this.livingSeats()) {
            if (tamer.role != WerewolfRole.BEAR_TAMER || this.powersLost) continue;

            boolean wolf = false;

            for (Seat seat : this.neighbours(tamer)) {
                if (seat.pack) wolf = true;
            }

            this.event(wolf ? "bear_growls" : "bear_quiet", Channel.ALL, 0, tamer.name, "", "");
        }
    }

    /** The newly charmed learn it, and every charmed player learns who else is charmed. */
    private void charmedWakeUp() {
        if (this.newlyCharmed.isEmpty()) return;

        StringBuilder names = new StringBuilder();

        for (Seat seat : this.livingSeats()) {
            if (!seat.charmed) continue;
            if (!names.isEmpty()) names.append(", ");

            names.append(seat.name);
        }

        for (Seat seat : this.livingSeats()) {
            if (seat.charmed || seat.role == WerewolfRole.PIPER)
                this.event("charmed", Channel.PRIVATE, seat.userId, names.toString(), "", "");
        }

        this.newlyCharmed.clear();
    }

    /** The living players just before and just after this one round the fire. */
    private List<Seat> neighbours(Seat seat) {
        List<Seat> circle = new ArrayList<>();

        for (Seat other : this.seats) {
            if (other.alive || other == seat) circle.add(other);
        }

        List<Seat> found = new ArrayList<>();
        int index = circle.indexOf(seat);
        int size = circle.size();

        if (size < 2 || index < 0) return found;

        Seat before = circle.get((index - 1 + size) % size);
        Seat after = circle.get((index + 1) % size);

        if (before != seat) found.add(before);
        if (after != seat && after != before) found.add(after);

        return found;
    }

    private void endVote() {
        Map<Integer, Integer> votes = this.votes();

        this.ravenMarked = 0;

        if (votes.isEmpty()) {
            this.event("vote_none", Channel.ALL, 0, "", "", "");
            return;
        }

        int accused = this.mostVoted(votes, false);
        Seat seat = this.seat(accused);

        if (seat == null) {
            Seat goat = this.pick(this.livingSeats(), other -> other.role == WerewolfRole.SCAPEGOAT);

            if (goat != null) {
                this.event("scapegoat", Channel.ALL, 0, goat.name, "", "");
                this.kill(goat, "scapegoat");
            } else {
                this.event("vote_tie", Channel.ALL, 0, "", "", "");
            }

            return;
        }

        if (seat.role == WerewolfRole.IDIOT && !seat.revealed && !this.powersLost) {
            seat.revealed = true;
            seat.canVote = false;
            this.event("idiot_spared", Channel.ALL, 0, seat.name, "", "");
            return;
        }

        this.kill(seat, "vote");
    }

    private void endHunter() {
        Seat hunter = this.hunters.pollFirst();

        if (hunter == null) return;

        Seat shot = this.seat(hunter.choice);

        if (shot == null || !shot.alive) {
            // The time ran out: the shot goes off anyway, at random.
            shot = this.pick(this.livingSeats(), seat -> seat != hunter);
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

        if (seat.pack) this.packLost = true;
        if (seat.role == WerewolfRole.HUNTER) this.hunters.addLast(seat);

        if (seat.role == WerewolfRole.ELDER
                && ("vote".equals(cause) || "poison".equals(cause) || "hunter".equals(cause))
                && !this.powersLost) {
            this.powersLost = true;
            this.event("elder_curse", Channel.ALL, 0, seat.name, "", "");
        }

        if (seat.role == WerewolfRole.KNIGHT && "wolves".equals(cause) && !this.powersLost) {
            Seat wolf = this.nextWolf(seat);

            if (wolf != null) this.rustyWolf = wolf.userId;
        }

        if (seat.role == WerewolfRole.ANGEL && this.night <= 1) this.angelWon = true;

        this.afterDeath(seat);
    }

    /** What a death sets off whatever its cause: the wild child loses its model, the lover dies of grief. */
    private void afterDeath(Seat seat) {
        for (Seat child : this.livingSeats()) {
            if (child.role == WerewolfRole.WILD_CHILD && child.model == seat.userId && !child.pack) {
                child.pack = true;
                this.event("wild_child_turns", Channel.PRIVATE, child.userId, seat.name, "", "");
                this.joinPack(child, "wild_child");
            }
        }

        Seat lover = this.seat(seat.lover);

        if (lover != null && lover.alive) this.kill(lover, "grief");
    }

    /** The first living werewolf after this seat round the fire (the knight's left). */
    private Seat nextWolf(Seat seat) {
        int index = this.seats.indexOf(seat);

        for (int offset = 1; offset < this.seats.size(); offset++) {
            Seat other = this.seats.get((index + offset) % this.seats.size());

            if (other.alive && other.pack) return other;
        }

        return null;
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

    /**
     * Ends the game when somebody has won: the angel (dead on the first day), the lovers alone, the piper (every other
     * living player charmed), the white wolf alone, the village (no werewolf and no piper left), the werewolves (only
     * the pack left, without the white wolf).
     */
    private boolean checkWin() {
        if (this.state != State.PLAYING) return true;

        List<Seat> alive = this.livingSeats();
        int pack = 0;
        boolean white = false;
        Seat piper = null;
        boolean allCharmed = true;

        for (Seat seat : alive) {
            if (seat.pack) pack++;
            if (seat.role == WerewolfRole.WHITE_WOLF) white = true;
            if (seat.role == WerewolfRole.PIPER) piper = seat;
            else if (!seat.charmed) allCharmed = false;
        }

        String camp = null;

        if (this.angelWon) {
            camp = "angel";
        } else if (alive.size() == 2
                && alive.get(0).lover == alive.get(1).userId
                && alive.get(0).pack != alive.get(1).pack) {
            camp = "lovers";
        } else if (alive.isEmpty()) {
            camp = "nobody";
        } else if (piper != null && allCharmed && alive.size() > 1) {
            camp = "piper";
        } else if (alive.size() == 1 && white) {
            camp = "white_wolf";
        } else if (pack == 0 && piper == null) {
            camp = "village";
        } else if (pack == alive.size() && !white) {
            camp = "wolves";
        }

        if (camp == null) return false;

        this.finish(camp);
        return true;
    }

    private void finish(String camp) {
        this.state = State.FINISHED;
        this.step = Step.NONE;
        this.winner = camp;
        this.event("win", Channel.ALL, 0, camp, "", "");

        for (Seat seat : this.seats) this.event("reveal", Channel.ALL, 0, seat.name, seat.role.code(), "");
    }

    /** Whether this player won: their camp, or their own win for the roles alone, or the lovers together. */
    public boolean won(Seat seat) {
        return switch (this.winner) {
            case "village" -> !seat.pack && seat.role != WerewolfRole.PIPER && seat.role != WerewolfRole.WHITE_WOLF;
            case "wolves" -> seat.pack && seat.role != WerewolfRole.WHITE_WOLF;
            case "white_wolf" -> seat.role == WerewolfRole.WHITE_WOLF;
            case "piper" -> seat.role == WerewolfRole.PIPER;
            case "angel" -> seat.role == WerewolfRole.ANGEL;
            case "lovers" -> seat.lover != 0;
            default -> false;
        };
    }

    /** The camp this player plays for now: village, wolves, or alone. */
    public String camp(Seat seat) {
        if (seat.role == WerewolfRole.WHITE_WOLF || seat.role == WerewolfRole.PIPER) return "alone";
        if (seat.role == WerewolfRole.ANGEL && this.night <= 1 && this.state == State.PLAYING) return "alone";
        if (seat.pack) return "wolves";

        return "village";
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
                case WOLF_DOG -> this.choose(bot.userId, this.random.nextBoolean(), now);
                case WILD_CHILD, FOX, RAVEN -> {
                    Seat target = this.pick(this.livingSeats(), seat -> seat != bot);

                    if (target != null) this.act(bot.userId, target.userId, 0, now);
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
                        Seat target = this.pick(this.livingSeats(), seat -> !seat.pack);

                        if (target != null) follow = target.userId;
                    }

                    if (follow != 0) this.act(bot.userId, follow, 0, now);
                }
                case ACCURSED -> this.choose(bot.userId, this.random.nextInt(100) < 60, now);
                case BIG_BAD_WOLF -> {
                    Seat target = this.pick(this.livingSeats(), seat -> !seat.pack && seat.userId != this.wolfVictim);

                    if (target != null) this.act(bot.userId, target.userId, 0, now);
                    else this.pass(bot.userId, now);
                }
                case WHITE_WOLF -> {
                    Seat target = this.pick(this.livingSeats(), seat -> seat.pack && seat != bot);

                    if (target != null && this.random.nextBoolean()) this.act(bot.userId, target.userId, 0, now);
                    else this.pass(bot.userId, now);
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
                case PIPER -> {
                    List<Seat> options = this.charmables(bot);

                    Collections.shuffle(options, this.random);

                    if (options.size() >= 2) this.act(bot.userId, options.get(0).userId, options.get(1).userId, now);
                    else if (options.size() == 1) this.act(bot.userId, options.get(0).userId, 0, now);
                    else bot.done = true;
                }
                case VOTE -> this.botVotes(bot, now);
                case HUNTER -> {
                    Seat target = this.pick(this.livingSeats(), seat -> seat != bot && !(bot.pack && seat.pack));

                    if (target == null) target = this.pick(this.livingSeats(), seat -> seat != bot);
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
     * A robot votes: a werewolf never against the pack (unless only the pack is left), a seer against a wolf it found,
     * the others with the crowd (two times in five) or at random.
     */
    private void botVotes(Seat bot, long now) {
        for (Map.Entry<Integer, WerewolfRole> entry : bot.seen.entrySet()) {
            Seat known = this.living(entry.getKey());

            if (known != null && known.pack) {
                this.act(bot.userId, known.userId, 0, now);
                return;
            }
        }

        boolean onlyPack = true;

        for (Seat seat : this.livingSeats()) {
            if (!seat.pack) onlyPack = false;
        }

        boolean spareThePack = bot.pack && !onlyPack;
        Seat target = this.living(this.mostVoted(this.votes(), true));

        if (target == null
                || target == bot
                || (spareThePack && target.pack)
                || bot.lover == target.userId
                || this.random.nextInt(100) < 60) {
            target = this.pick(
                    this.livingSeats(),
                    seat -> seat != bot && seat.userId != bot.lover && !(spareThePack && seat.pack));
        }

        if (target == null) target = this.pick(this.livingSeats(), seat -> seat != bot);
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
        Seat other = this.pick(this.livingSeats(), seat -> seat != bot && !(bot.pack && seat.pack));

        this.event(
                "bot_line",
                Channel.ALL,
                0,
                bot.name,
                String.valueOf(this.random.nextInt(12)),
                other == null ? "" : other.name);
    }

    private Seat pick(List<Seat> seats, Predicate<Seat> filter) {
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
        WerewolfRole role = seat.role;

        return switch (this.step) {
            case CUPID -> role == WerewolfRole.CUPID;
            case WOLF_DOG -> role == WerewolfRole.WOLF_DOG;
            case WILD_CHILD -> role == WerewolfRole.WILD_CHILD;
            case GUARD -> role == WerewolfRole.GUARD && !this.powersLost;
            case SEER -> role == WerewolfRole.SEER && !this.powersLost;
            case FOX -> role == WerewolfRole.FOX && !this.powersLost && this.foxActive;
            case RAVEN -> role == WerewolfRole.RAVEN && !this.powersLost;
            case WOLVES -> seat.pack;
            case ACCURSED -> role == WerewolfRole.ACCURSED_FATHER && this.wolfVictim != 0 && !this.infectUsed;
            case BIG_BAD_WOLF -> role == WerewolfRole.BIG_BAD_WOLF && !this.packLost;
            case WHITE_WOLF -> role == WerewolfRole.WHITE_WOLF && this.otherPack(seat);
            case WITCH -> role == WerewolfRole.WITCH && !this.powersLost;
            case PIPER -> role == WerewolfRole.PIPER && !this.charmables(seat).isEmpty();
            case DEBATE -> true;
            case VOTE -> seat.canVote;
            default -> false;
        };
    }

    private boolean otherPack(Seat seat) {
        for (Seat other : this.livingSeats()) {
            if (other != seat && other.pack) return true;
        }

        return false;
    }

    private Seat charmable(int userId, Seat piper) {
        Seat seat = this.living(userId);

        return seat == null || seat == piper || seat.charmed ? null : seat;
    }

    private List<Seat> charmables(Seat piper) {
        List<Seat> seats = new ArrayList<>();

        for (Seat seat : this.livingSeats()) {
            if (seat != piper && !seat.charmed) seats.add(seat);
        }

        return seats;
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

    private String packNames() {
        StringBuilder names = new StringBuilder();

        for (Seat seat : this.seats) {
            if (!seat.pack || !seat.alive) continue;
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

    /** Whether this player is the little girl, peeking at the pack (without seeing who speaks). */
    public boolean peeks(Seat reader) {
        return reader != null
                && reader.alive
                && !reader.pack
                && reader.role == WerewolfRole.LITTLE_GIRL
                && !this.powersLost
                && this.state == State.PLAYING;
    }

    /** Who may read this event. */
    public boolean visible(Event event, Seat reader) {
        if (reader == null) return event.channel() == Channel.ALL;

        return switch (event.channel()) {
            case ALL -> true;
            case WOLVES ->
                reader.pack
                        || this.state == State.FINISHED
                        || (this.peeks(reader) && ("wolf_choice".equals(event.type()) || "chat".equals(event.type())));
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

    /** The day's votes so far, with the raven's two votes: accused id to the number of votes. */
    public Map<Integer, Integer> votes() {
        Map<Integer, Integer> votes = new HashMap<>();

        if (this.step != Step.VOTE) return votes;

        for (Seat seat : this.livingSeats()) {
            if (seat.choice != 0 && seat.canVote) votes.merge(seat.choice, 1, Integer::sum);
        }

        if (this.living(this.ravenMarked) != null) votes.merge(this.ravenMarked, 2, Integer::sum);

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

    public boolean powersLost() {
        return this.powersLost;
    }

    public boolean foxActive() {
        return this.foxActive;
    }

    public int ravenMarked() {
        return this.ravenMarked;
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
}
