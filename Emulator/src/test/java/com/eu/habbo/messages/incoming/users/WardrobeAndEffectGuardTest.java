package com.eu.habbo.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * Neither effect packet may hand out an effect. 2959 used to fall through to
     * {@code room.giveEffect} for anything the user did not own, gated only by
     * special_enables - 102, 136 and 178 by default - so every catalogue effect, the club
     * ones and the Freeze and Banzai team colours were free for the asking. Granting is
     * the staff command's job, where cmd_enable and the per-rank block list are checked.
     */
    @Test
    void neitherEffectPacketGrantsAnEffectTheUserDoesNotOwn() throws Exception {
        String activate = source("ActivateEffectEvent");
        String enable = source("EnableEffectEvent");

        assertTrue(activate.contains("ownsEffect(effectId)"), "2959 must gate on ownership");
        assertFalse(activate.contains("giveEffect("), "2959 must not put an effect on anyone: " + activate);

        assertTrue(enable.contains("ownsEffect(effectId)"), "1752 must gate on ownership");
        // 1752 keeps its giveEffect, but only on the branch that takes the effect off.
        assertTrue(enable.contains("giveEffect(this.client.getHabbo().getRoomUnit(), 0, -1)"), enable);
    }

    private static String source(String handler) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/users/" + handler + ".java"));
    }
}
