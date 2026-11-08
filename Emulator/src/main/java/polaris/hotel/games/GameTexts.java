package polaris.hotel.games;

import java.util.Arrays;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.core.TextsManager;

/**
 * What the Game Center's games say and are named in the hotel's language: a text of `emulator_texts`, or the
 * English default when the hotel has not set it (and in the tests, where no emulator runs).
 */
public final class GameTexts {

    private GameTexts() {}

    /** A text of the hotel, or the default. */
    public static String text(String key, String fallback) {
        TextsManager texts = Emulator.getTexts();

        return texts == null ? fallback : texts.getValueQuietly(key, fallback);
    }

    /** A setting of the hotel (`emulator_settings`), or the default. */
    public static String setting(String key, String fallback) {
        ConfigurationManager config = Emulator.getConfig();

        return config == null ? fallback : config.getValue(key, fallback);
    }

    /** A game's robots' names: `games.bots.<game>` (names separated by ;), or the defaults. */
    public static String[] botNames(String game, String[] defaults) {
        String[] names = Arrays.stream(text("games.bots." + game, "").split(";"))
                .map(String::trim)
                .filter(name -> !name.isEmpty() && name.length() <= 25)
                .toArray(String[]::new);

        return names.length > 0 ? names : defaults;
    }
}
