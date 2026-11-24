package polaris.hotel.quests;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A bot or a pet given as a reward (the official client's product types 6 and 10), read from the prize's extra
 * parameters.
 *
 * <ul>
 *   <li>Bot: {@code "<ai type>;name:<name>;figure:<figure>;gender:<M|F>;motto:<motto>"}, the form a catalog bot
 *       carries. Only the figure is required; the type defaults to "generic".
 *   <li>Pet: {@code "<type> <race> <colour> [name]"}, e.g. {@code "15 2 FFFFFF Horse"}. Without a name the pet
 *       comes as a package the winner names, where the official package for its type exists.
 * </ul>
 */
public final class CompanionPrize {
    static final String DEFAULT_BOT_TYPE = "generic";
    static final String DEFAULT_BOT_NAME = "Bot";
    static final String DEFAULT_PET_NAME = "Pet";
    static final int MAX_NAME_LENGTH = 25;

    private CompanionPrize() {}

    /** A bot prize: its AI type and the row a new bot is created from (name, motto, figure, gender). */
    public record Bot(String type, Map<String, String> data) {
        public String figure() {
            return this.data.get("figure");
        }
    }

    /**
     * A pet prize; {@link #figure()} is what the client draws it from. The name is null when the prize gives none: the
     * winner then names the pet, where a package for its type exists.
     */
    public record Pet(int type, int race, String color, String name) {
        /** The name the pet gets when it is created without the winner naming it. */
        public String nameOrDefault() {
            return this.name == null ? DEFAULT_PET_NAME : this.name;
        }

        public String figure() {
            return this.type + " " + this.race + " " + this.color;
        }
    }

    /** The bot the extra parameters describe, or null when they name no figure. */
    public static Bot bot(String extra) {
        if (extra == null || extra.isBlank()) return null;

        String type = DEFAULT_BOT_TYPE;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("name", DEFAULT_BOT_NAME);
        data.put("motto", "");
        data.put("gender", "M");

        for (String part : extra.split(";")) {
            String entry = part.trim();
            if (entry.isEmpty()) continue;

            int colon = entry.indexOf(':');
            if (colon < 0) {
                type = entry.toLowerCase(java.util.Locale.ROOT);
                continue;
            }

            String key = entry.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String value = entry.substring(colon + 1).trim();
            if (key.equals("name") || key.equals("motto") || key.equals("figure") || key.equals("gender")) {
                data.put(key, value);
            }
        }

        String figure = data.get("figure");
        if (figure == null || figure.isBlank()) return null;

        data.put("gender", data.get("gender").equalsIgnoreCase("F") ? "F" : "M");
        data.put("name", clip(data.get("name").isBlank() ? DEFAULT_BOT_NAME : data.get("name")));
        return new Bot(type, data);
    }

    /** The pet the extra parameters describe, or null when they do not start with a pet type. */
    public static Pet pet(String extra) {
        if (extra == null || extra.isBlank()) return null;

        String[] parts = extra.trim().split("\\s+", 4);
        try {
            int type = Integer.parseInt(parts[0]);
            int race = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (type < 0 || race < 0) return null;

            String color = parts.length > 2 ? parts[2].toUpperCase(java.util.Locale.ROOT) : "FFFFFF";
            if (!color.matches("[0-9A-F]{6}")) return null;

            String name = parts.length > 3 && !parts[3].isBlank() ? clip(parts[3].trim()) : null;
            return new Pet(type, race, color, name);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clip(String name) {
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
