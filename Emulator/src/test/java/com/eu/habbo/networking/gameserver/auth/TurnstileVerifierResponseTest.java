package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class TurnstileVerifierResponseTest {

    @Test
    void nonSuccessHttpStatusIsReportedAsDependencyFailure() {
        assertThrows(IOException.class, () -> TurnstileVerifier.parseVerificationResponse(503, "{}", "127.0.0.1"));
    }

    @Test
    void validRejectionResponseRemainsACompletedDependencyCall() throws Exception {
        assertFalse(TurnstileVerifier.parseVerificationResponse(200, "{\"success\":false}", "127.0.0.1"));
    }
}
