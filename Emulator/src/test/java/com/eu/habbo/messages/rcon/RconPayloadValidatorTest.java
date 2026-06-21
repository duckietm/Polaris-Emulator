package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RconPayloadValidatorTest {
    @Test
    void acceptsValidAnnotatedPayloads() {
        SetRank.JSONSetRank payload = new SetRank.JSONSetRank();
        payload.user_id = 1;
        payload.rank = 2;

        assertNull(RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsInvalidSetRankPayloadsBeforeDispatch() {
        SetRank.JSONSetRank payload = new SetRank.JSONSetRank();
        payload.user_id = 0;
        payload.rank = 2;

        assertEquals("invalid user", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsInvalidGrantPayloadsBeforeDispatch() {
        GiveCredits.JSONGiveCredits payload = new GiveCredits.JSONGiveCredits();
        payload.user_id = 1;
        payload.credits = 0;

        assertEquals("invalid credits", RconPayloadValidator.validate(payload));
    }

    @Test
    void rejectsBlankBadgePayloadsBeforeDispatch() {
        GiveBadge.GiveBadgeJSON payload = new GiveBadge.GiveBadgeJSON();
        payload.user_id = 1;
        payload.badge = " ";

        assertEquals("invalid badge", RconPayloadValidator.validate(payload));
    }
}
