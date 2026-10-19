package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class WordFilterNormaliseTest {
    private static final Pattern DIACRITICS_AND_FRIENDS =
            Pattern.compile("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");

    /** The chain as it was written with String.replaceAll, which compiles a pattern on every call. */
    private static String reference(String message) {
        return DIACRITICS_AND_FRIENDS
                .matcher(Normalizer.normalize(StringUtils.stripAccents(message), Normalizer.Form.NFKD)
                        .replaceAll("[,.;:'\"]", " ")
                        .replace("I", "l")
                        .replaceAll("[^\\p{ASCII}*$]", "")
                        .replaceAll("\\p{M}", " ")
                        .replaceAll("^\\p{M}*$]", "")
                        .replaceAll("[1|]", "i")
                        .replace("2", "z")
                        .replace("3", "e")
                        .replace("4", "a")
                        .replace("5", "s")
                        .replace("8", "b")
                        .replace("0", "o")
                        .replace(" ", " ")
                        .replace("$", "s")
                        .replace("ß", "b")
                        .trim())
                .replaceAll(" ");
    }

    @Test
    void normalisingGivesTheSameTextAsTheOriginalChain() {
        WordFilter filter = mock(WordFilter.class, CALLS_REAL_METHODS);
        String[] samples = {
            "",
            "hello world",
            "H3ll0, W0rld; 5 $ 8 | 1",
            "Ça va très bien, merci",
            "ｆｕｌｌｗｉｄｔｈ ｔｅｘｔ",
            "Straße 2 dae  spaces  ",
            "I love Illinois: 'quoted' \"text\"",
            "é combining ́ marks",
            "emoji 😀 and * star"
        };

        for (String sample : samples) {
            assertEquals(reference(sample), filter.normalise(sample), "for input: " + sample);
        }
    }
}
