package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class WiredTextInputCapturePatternTest {

    @Test
    void aLineFullOfSeparatorsCannotStallTheMatcher() {
        String template = "#a#,#b#,#c#,#d#,#e#,#f#,#g#,#h#!";
        String line = ",".repeat(250);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Matcher matcher =
                    WiredTextInputCaptureSupport.templatePattern(template).matcher(line);
            assertFalse(matcher.find());
        });
    }

    @Test
    void valuesStillEndAtTheirSeparator() {
        Matcher matcher = WiredTextInputCaptureSupport.templatePattern("give #a# to #b# now")
                .matcher("GIVE 12 to 7 now");

        assertTrue(matcher.matches());
        assertEquals("12", matcher.group(1));
        assertEquals("7", matcher.group(2));
    }

    @Test
    void theSamePatternIsReusedForATemplate() {
        assertTrue(WiredTextInputCaptureSupport.templatePattern("#a#-#b#")
                == WiredTextInputCaptureSupport.templatePattern("#a#-#b#"));
    }

    @Test
    void adjacentMarkersNeverMatch() {
        for (String template : new String[] {"#a##b#", "qr:#(a)#(b)", "#(a)#(b)"}) {
            Matcher matcher =
                    WiredTextInputCaptureSupport.templatePattern(template).matcher(template);

            assertFalse(matcher.find(), template);
            assertFalse(
                    WiredTextInputCaptureSupport.templatePattern(template)
                            .matcher("12")
                            .find(),
                    template);
        }
    }
}
