package com.eu.habbo.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Profiles and mottos ran database queries for every packet, with no rate limit. */
class ProfileAndMottoRateLimitTest {

    @Test
    void bothHandlersAreRateLimited() {
        assertTrue(new RequestUserProfileEvent().getRatelimit() > 0);
        assertTrue(new SaveMottoEvent().getRatelimit() > 0);
    }

    @Test
    void anUnchangedMottoIsNotWrittenAgain() throws Exception {
        String motto =
                Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/users/SaveMottoEvent.java"));

        int unchanged = motto.indexOf("if (!changed) return;");
        assertTrue(unchanged > 0 && unchanged < motto.indexOf("setMotto(motto)"));
    }
}
