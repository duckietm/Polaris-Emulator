package com.eu.habbo.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WardrobeAndEffectGuardTest {
    @Test
    void wardrobeAcceptsTheClientsTenSlotsAndBoundsTheLook() {
        assertEquals(10, SaveWardrobeEvent.MAX_SLOT_ID);
        assertEquals(256, SaveWardrobeEvent.MAX_LOOK_LENGTH);
        assertTrue(new SaveWardrobeEvent().getRatelimit() > 0);
    }

    @Test
    void effectToggleAndActivationAreRateLimited() {
        assertTrue(new ActivateEffectEvent().getRatelimit() > 0);
        assertTrue(new EnableEffectEvent().getRatelimit() > 0);
    }
}
