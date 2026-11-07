package polaris.hotel.games.lostmonkey;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A player's island in Lost Monkey (Habbo's iPhone game): the monkey's needs (hunger, energy, hygiene, fun, from 0 to
 * 100, going down with time), the bananas picked and the coins won, the banana trees growing their bunches again,
 * and the rocket built part by part on the beach to leave the island. Pure rules: the time is given, the state is
 * saved by {@link LostMonkeyManager}.
 */
public final class LostMonkeyIsland {

    static final int TREES = 3;
    static final int BUNCHES = 3;
    /** A tree grows a bunch back in this time. */
    static final long REGROW_MS = 15 * 60_000L;
    /** The needs go from 100 to 0 in these times (hunger, energy awake, hygiene, fun). */
    static final double HUNGER_MS = 10 * 3_600_000d;

    static final double ENERGY_MS = 14 * 3_600_000d;
    static final double HYGIENE_MS = 18 * 3_600_000d;
    static final double FUN_MS = 8 * 3_600_000d;
    /** Asleep, the energy comes back from 0 to 100 in this time. */
    static final double SLEEP_MS = 20 * 60_000d;

    static final long TV_COOLDOWN_MS = 30_000L;
    static final int BANANA_HUNGER = 20;
    static final int BANANA_PRICE = 4;
    /** The coins each part of the rocket costs, from the bare workbench to the rocket ready to fly. */
    static final int[] PARTS = {25, 40, 60, 80, 110, 150, 200};

    public static final int ROCKET_READY = PARTS.length;
    static final int LAUNCH_REWARD = 100;
    /** The coins the island's games can give a day. */
    static final int DAILY_COINS = 400;

    double hunger = 80;
    double energy = 90;
    double hygiene = 90;
    double fun = 70;
    int bananas = 3;
    int coins = 20;
    int rocket;
    int launches;
    int colour;
    boolean asleep;
    long tvAt;
    final long[] treeAt = new long[TREES];
    final int[] treeBunches = {BUNCHES, BUNCHES, BUNCHES};
    long updated;
    long coinDay;
    int coinsToday;

    /** A new island, its trees full. */
    static LostMonkeyIsland fresh(long now, int colour) {
        LostMonkeyIsland island = new LostMonkeyIsland();

        island.updated = now;
        island.colour = Math.floorMod(colour, 9);

        for (int tree = 0; tree < TREES; tree++) island.treeAt[tree] = now;

        return island;
    }

    /** The time passes: the needs go down (the energy comes back while asleep), the trees grow. */
    void advance(long now) {
        double elapsed = Math.max(0, now - this.updated);

        if (elapsed <= 0) return;

        this.hunger = clamp(this.hunger - 100 * elapsed / HUNGER_MS);
        this.hygiene = clamp(this.hygiene - 100 * elapsed / HYGIENE_MS);
        this.fun = clamp(this.fun - 100 * elapsed / FUN_MS);
        this.energy =
                clamp(this.asleep ? this.energy + 100 * elapsed / SLEEP_MS : this.energy - 100 * elapsed / ENERGY_MS);

        // Rested, the monkey wakes up on its own.
        if (this.asleep && this.energy >= 100) this.asleep = false;

        for (int tree = 0; tree < TREES; tree++) {
            while (this.treeBunches[tree] < BUNCHES && now - this.treeAt[tree] >= REGROW_MS) {
                this.treeBunches[tree]++;
                this.treeAt[tree] += REGROW_MS;
            }

            if (this.treeBunches[tree] >= BUNCHES) this.treeAt[tree] = now;
        }

        this.updated = now;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private void awake() {
        if (this.asleep) throw new LostMonkeyException("asleep");
    }

    /** Picks the bunches of a tree: three bananas a bunch. */
    void harvest(int tree) {
        this.awake();
        if (tree < 0 || tree >= TREES) throw new LostMonkeyException("no_tree");
        if (this.treeBunches[tree] == 0) throw new LostMonkeyException("no_bananas");

        if (this.treeBunches[tree] == BUNCHES) this.treeAt[tree] = this.updated;

        this.bananas += this.treeBunches[tree] * 3;
        this.treeBunches[tree] = 0;
    }

    void feed() {
        this.awake();
        if (this.bananas <= 0) throw new LostMonkeyException("no_bananas");
        if (this.hunger >= 100) throw new LostMonkeyException("not_hungry");

        this.bananas--;
        this.hunger = clamp(this.hunger + BANANA_HUNGER);
        this.fun = clamp(this.fun + 2);
    }

    void wash() {
        this.awake();
        this.hygiene = 100;
    }

    void watchTv(long now) {
        this.awake();
        if (now - this.tvAt < TV_COOLDOWN_MS) throw new LostMonkeyException("too_soon");

        this.tvAt = now;
        this.fun = clamp(this.fun + 30);
        this.energy = clamp(this.energy - 3);
    }

    void sleep() {
        this.asleep = true;
    }

    void wake() {
        this.asleep = false;
    }

    void buyBananas(int count) {
        this.awake();
        if (count <= 0 || count > 20) throw new LostMonkeyException("bad_count");
        if (this.coins < count * BANANA_PRICE) throw new LostMonkeyException("no_coins");

        this.coins -= count * BANANA_PRICE;
        this.bananas += count;
    }

    /** The next part of the rocket, bought with coins. */
    void buildRocket() {
        this.awake();
        if (this.rocket >= ROCKET_READY) throw new LostMonkeyException("rocket_ready");
        if (this.coins < PARTS[this.rocket]) throw new LostMonkeyException("no_coins");

        this.coins -= PARTS[this.rocket];
        this.rocket++;
    }

    /** The rocket flies: the monkey sees the world and comes back for another one. */
    void launch() {
        this.awake();
        if (this.rocket < ROCKET_READY) throw new LostMonkeyException("rocket_not_ready");

        this.rocket = 0;
        this.launches++;
        this.coins += LAUNCH_REWARD;
        this.fun = 100;
    }

    /** One of the island's games is played: it tires and amuses the monkey. */
    void play() {
        this.awake();
        if (this.energy < 10) throw new LostMonkeyException("too_tired");

        this.energy = clamp(this.energy - 8);
        this.fun = clamp(this.fun + 15);
    }

    /** The coins of a game, a tenth of its score, within the day's limit; answers the coins given. */
    int reward(int score, long day) {
        if (day != this.coinDay) {
            this.coinDay = day;
            this.coinsToday = 0;
        }

        int coins = Math.max(0, Math.min(DAILY_COINS - this.coinsToday, Math.min(80, score / 10)));

        this.coins += coins;
        this.coinsToday += coins;
        return coins;
    }

    void setColour(int colour) {
        this.colour = Math.floorMod(colour, 9);
    }

    /** The monkey's mood, from 0 (happy) to 4 (miserable): the lowest of its needs. */
    int mood() {
        double lowest = Math.min(Math.min(this.hunger, this.energy), Math.min(this.hygiene, this.fun));

        return lowest >= 70 ? 0 : lowest >= 50 ? 1 : lowest >= 30 ? 2 : lowest >= 15 ? 3 : 4;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray trees = new JsonArray();
        JsonArray grown = new JsonArray();

        for (int tree = 0; tree < TREES; tree++) {
            trees.add(this.treeBunches[tree]);
            grown.add(this.treeAt[tree]);
        }

        json.addProperty("hunger", this.hunger);
        json.addProperty("energy", this.energy);
        json.addProperty("hygiene", this.hygiene);
        json.addProperty("fun", this.fun);
        json.addProperty("bananas", this.bananas);
        json.addProperty("coins", this.coins);
        json.addProperty("rocket", this.rocket);
        json.addProperty("launches", this.launches);
        json.addProperty("colour", this.colour);
        json.addProperty("asleep", this.asleep);
        json.addProperty("tvAt", this.tvAt);
        json.add("trees", trees);
        json.add("treeAt", grown);
        json.addProperty("updated", this.updated);
        json.addProperty("coinDay", this.coinDay);
        json.addProperty("coinsToday", this.coinsToday);
        return json;
    }

    static LostMonkeyIsland fromJson(JsonObject json, long now) {
        LostMonkeyIsland island = fresh(now, 0);

        try {
            island.hunger = json.get("hunger").getAsDouble();
            island.energy = json.get("energy").getAsDouble();
            island.hygiene = json.get("hygiene").getAsDouble();
            island.fun = json.get("fun").getAsDouble();
            island.bananas = json.get("bananas").getAsInt();
            island.coins = json.get("coins").getAsInt();
            island.rocket =
                    Math.max(0, Math.min(ROCKET_READY, json.get("rocket").getAsInt()));
            island.launches = json.get("launches").getAsInt();
            island.colour = Math.floorMod(json.get("colour").getAsInt(), 9);
            island.asleep = json.get("asleep").getAsBoolean();
            island.tvAt = json.get("tvAt").getAsLong();
            island.updated = json.get("updated").getAsLong();
            island.coinDay = json.has("coinDay") ? json.get("coinDay").getAsLong() : 0;
            island.coinsToday = json.has("coinsToday") ? json.get("coinsToday").getAsInt() : 0;

            JsonArray trees = json.getAsJsonArray("trees");
            JsonArray grown = json.getAsJsonArray("treeAt");

            for (int tree = 0; tree < TREES && tree < trees.size(); tree++) {
                island.treeBunches[tree] =
                        Math.max(0, Math.min(BUNCHES, trees.get(tree).getAsInt()));
                island.treeAt[tree] = grown.get(tree).getAsLong();
            }
        } catch (RuntimeException e) {
            // A damaged island starts again.
            return fresh(now, 0);
        }

        return island;
    }

    /** What the player sees: the state, and when the trees grow their next bunch. */
    JsonObject view(long now) {
        JsonObject view = this.toJson();
        JsonArray next = new JsonArray();

        for (int tree = 0; tree < TREES; tree++) {
            next.add(this.treeBunches[tree] >= BUNCHES ? 0 : Math.max(0, this.treeAt[tree] + REGROW_MS - now));
        }

        JsonArray parts = new JsonArray();

        for (int price : PARTS) parts.add(price);

        view.add("nextBunch", next);
        view.add("parts", parts);
        view.addProperty("rocketReady", ROCKET_READY);
        view.addProperty("bananaPrice", BANANA_PRICE);
        view.addProperty("mood", this.mood());
        view.addProperty("dailyCoins", DAILY_COINS);
        view.addProperty("tvIn", Math.max(0, this.tvAt + TV_COOLDOWN_MS - now));
        return view;
    }
}
