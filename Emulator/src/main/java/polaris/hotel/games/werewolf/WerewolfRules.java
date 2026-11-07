package polaris.hotel.games.werewolf;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules a village plays with, chosen by its host: how many players, which special roles, robots, whether the role
 * of the dead is shown, and the pace. The werewolves are one for up to 5 players, two up to 8, three beyond.
 */
public record WerewolfRules(
        int maxPlayers,
        boolean seer,
        boolean witch,
        boolean hunter,
        boolean cupid,
        boolean guard,
        boolean bots,
        boolean revealRoles,
        int speed) {

    public static final int MIN_PLAYERS = 5;
    public static final int MAX_CHAIRS = 12;

    public static WerewolfRules standard() {
        return new WerewolfRules(8, true, true, true, true, false, true, true, 2);
    }

    public static int wolves(int players) {
        return players <= 5 ? 1 : players <= 8 ? 2 : 3;
    }

    /** The roles dealt to this many players: the werewolves, the special roles that fit, villagers for the rest. */
    public List<WerewolfRole> composition(int players) {
        List<WerewolfRole> roles = new ArrayList<>();

        for (int wolf = 0; wolf < wolves(players); wolf++) roles.add(WerewolfRole.WEREWOLF);

        WerewolfRole[] specials = {
            WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.HUNTER, WerewolfRole.CUPID, WerewolfRole.GUARD
        };
        boolean[] chosen = {this.seer, this.witch, this.hunter, this.cupid, this.guard};

        // At least one plain villager stays in the village.
        for (int index = 0; index < specials.length && roles.size() < players - 1; index++) {
            if (chosen[index]) roles.add(specials[index]);
        }

        while (roles.size() < players) roles.add(WerewolfRole.VILLAGER);

        return roles;
    }

    /** Seconds for a night step, the debate, the vote and the hunter's shot, at this pace (1 fast, 2 normal, 3 slow). */
    public int nightSeconds() {
        return new int[] {20, 30, 40}[this.speed - 1];
    }

    public int debateSeconds() {
        return new int[] {60, 100, 160}[this.speed - 1];
    }

    public int voteSeconds() {
        return new int[] {25, 35, 45}[this.speed - 1];
    }

    public int hunterSeconds() {
        return new int[] {15, 20, 25}[this.speed - 1];
    }

    /** The rules a host asked for, kept within bounds; what they did not send stays as it was. */
    public static WerewolfRules from(JsonObject body, WerewolfRules base) {
        if (body == null) return base;

        return new WerewolfRules(
                clamp(intOf(body, "maxPlayers", base.maxPlayers()), MIN_PLAYERS, MAX_CHAIRS),
                boolOf(body, "seer", base.seer()),
                boolOf(body, "witch", base.witch()),
                boolOf(body, "hunter", base.hunter()),
                boolOf(body, "cupid", base.cupid()),
                boolOf(body, "guard", base.guard()),
                boolOf(body, "bots", base.bots()),
                boolOf(body, "revealRoles", base.revealRoles()),
                clamp(intOf(body, "speed", base.speed()), 1, 3));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("maxPlayers", this.maxPlayers);
        json.addProperty("seer", this.seer);
        json.addProperty("witch", this.witch);
        json.addProperty("hunter", this.hunter);
        json.addProperty("cupid", this.cupid);
        json.addProperty("guard", this.guard);
        json.addProperty("bots", this.bots);
        json.addProperty("revealRoles", this.revealRoles);
        json.addProperty("speed", this.speed);
        return json;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int intOf(JsonObject body, String key, int fallback) {
        try {
            return body.has(key) ? body.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean boolOf(JsonObject body, String key, boolean fallback) {
        try {
            return body.has(key) ? body.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
