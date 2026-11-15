package polaris.hotel.bots.dialogue;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of a bot dialogue: what the bot says and the options the player may pick. Everything is cut to size here,
 * so whatever a dialogue builds, the client receives a bounded page: the text and labels lose control characters and
 * are cut to {@link #MAX_TEXT} and {@link #MAX_LABEL}, and at most {@link #MAX_OPTIONS} options are kept.
 */
public record BotDialoguePage(String text, List<Option> options) {
    public static final int MAX_TEXT = 200;
    public static final int MAX_LABEL = 40;
    public static final int MAX_OPTIONS = 6;
    public static final int MAX_KEY = 64;

    /** An option: the label the player reads and the key the dialogue gets back. Keys stay on the server. */
    public record Option(String key, String label) {
        public Option {
            key = key == null ? "" : key;

            if (key.isBlank() || key.length() > MAX_KEY) {
                throw new IllegalArgumentException("An option key is 1 to " + MAX_KEY + " characters");
            }

            label = clean(label, MAX_LABEL);
        }
    }

    public BotDialoguePage {
        text = clean(text, MAX_TEXT);
        List<Option> kept = new ArrayList<>();

        if (options != null) {
            for (Option option : options) {
                if (option != null && kept.size() < MAX_OPTIONS) {
                    kept.add(option);
                }
            }
        }

        options = List.copyOf(kept);
    }

    public static BotDialoguePage of(String text, Option... options) {
        return new BotDialoguePage(text, List.of(options));
    }

    public static Option option(String key, String label) {
        return new Option(key, label);
    }

    /** One line, no control characters, at most {@code max} characters. */
    static String clean(String value, int max) {
        if (value == null) {
            return "";
        }

        String line = value.replaceAll("[\\p{Cntrl}\\p{Cf}]+", " ").strip();
        return line.length() > max ? line.substring(0, max) : line;
    }
}
