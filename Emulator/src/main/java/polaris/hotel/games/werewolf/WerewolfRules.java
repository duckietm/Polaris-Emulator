package polaris.hotel.games.werewolf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The rules a village plays with, chosen by its host: how many players, how many werewolves (0: as many as the village
 * calls for), the special roles put in the village, robots, whether the role of the dead is shown, and the pace.
 * When more roles are chosen than there are places, the roles of the game are drawn among them.
 */
public record WerewolfRules(
        int maxPlayers, int wolves, Set<WerewolfRole> roles, boolean bots, boolean revealRoles, int speed) {

    public static final int MIN_PLAYERS = 5;
    public static final int MAX_CHAIRS = 12;

    public WerewolfRules {
        roles = roles.isEmpty() ? EnumSet.noneOf(WerewolfRole.class) : EnumSet.copyOf(roles);
        roles.remove(WerewolfRole.WEREWOLF);
        roles.remove(WerewolfRole.VILLAGER);
        roles = Collections.unmodifiableSet(roles);
    }

    public static WerewolfRules standard() {
        return new WerewolfRules(
                8,
                0,
                EnumSet.of(
                        WerewolfRole.SEER,
                        WerewolfRole.WITCH,
                        WerewolfRole.HUNTER,
                        WerewolfRole.CUPID,
                        WerewolfRole.LITTLE_GIRL),
                true,
                true,
                2);
    }

    /** The werewolves a village of this many players calls for: one up to 5, two up to 8, three beyond. */
    public static int autoWolves(int players) {
        return players <= 5 ? 1 : players <= 8 ? 2 : 3;
    }

    /** The werewolves of a game of this many players: the host's number, kept under half of the village. */
    public int wolvesFor(int players) {
        int most = Math.max(1, (players - 1) / 2);

        return this.wolves <= 0 ? autoWolves(players) : Math.min(this.wolves, most);
    }

    /**
     * The roles dealt to this many players: the werewolves (the special werewolves chosen among them), then the other
     * roles chosen, drawn when they do not all fit (the sisters take two places), and villagers for the rest.
     */
    public List<WerewolfRole> composition(int players, Random random) {
        List<WerewolfRole> roles = new ArrayList<>();
        int wolves = this.wolvesFor(players);
        List<WerewolfRole> wolfRoles = new ArrayList<>();
        List<WerewolfRole> others = new ArrayList<>();

        for (WerewolfRole role : this.roles) {
            if (role.wolf()) wolfRoles.add(role);
            else others.add(role);
        }

        Collections.shuffle(wolfRoles, random);
        Collections.shuffle(others, random);

        for (WerewolfRole role : wolfRoles) {
            if (roles.size() < wolves) roles.add(role);
        }

        while (roles.size() < wolves) roles.add(WerewolfRole.WEREWOLF);

        for (WerewolfRole role : others) {
            int places = role == WerewolfRole.SISTER ? 2 : 1;

            if (roles.size() + places > players) continue;

            for (int place = 0; place < places; place++) roles.add(role);
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
        return new int[] {20, 25, 30}[this.speed - 1];
    }

    /** The rules a host asked for, kept within bounds; what they did not send stays as it was. */
    public static WerewolfRules from(JsonObject body, WerewolfRules base) {
        if (body == null) return base;

        Set<WerewolfRole> roles =
                base.roles().isEmpty() ? EnumSet.noneOf(WerewolfRole.class) : EnumSet.copyOf(base.roles());

        if (body.has("roles") && body.get("roles").isJsonArray()) {
            roles = EnumSet.noneOf(WerewolfRole.class);

            for (JsonElement element : body.getAsJsonArray("roles")) {
                try {
                    WerewolfRole role = WerewolfRole.fromCode(element.getAsString());

                    if (role != null) roles.add(role);
                } catch (RuntimeException e) {
                    // Not a role: left out.
                }
            }
        }

        return new WerewolfRules(
                clamp(intOf(body, "maxPlayers", base.maxPlayers()), MIN_PLAYERS, MAX_CHAIRS),
                clamp(intOf(body, "wolves", base.wolves()), 0, 5),
                roles,
                boolOf(body, "bots", base.bots()),
                boolOf(body, "revealRoles", base.revealRoles()),
                clamp(intOf(body, "speed", base.speed()), 1, 3));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray roles = new JsonArray();

        for (WerewolfRole role : this.roles) roles.add(role.code());

        json.addProperty("maxPlayers", this.maxPlayers);
        json.addProperty("wolves", this.wolves);
        json.add("roles", roles);
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
