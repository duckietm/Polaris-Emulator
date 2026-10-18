package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectControlClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScore;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScoreToTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRelativeMove;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWhisper;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredObservedEffectOrderTest {

    private static final Item BASE = mock(Item.class);

    @Test
    void matchRunsAfterToggleAndMoveWhicheverIsHigher() {
        IWiredEffect match = new WiredEffectMatchFurni(1, 1, BASE, "", 0, 0);
        IWiredEffect toggle = new WiredEffectToggleFurni(2, 1, BASE, "", 0, 0);
        IWiredEffect move = new WiredEffectRelativeMove(3, 1, BASE, "", 0, 0);
        IWiredEffect other = context -> {};

        assertEquals(
                List.of(other, toggle, move, match),
                WiredObservedEffectOrder.apply(List.of(match, other, toggle, move)));
    }

    @Test
    void showMessageRunsAfterChangeVariableAndOthersKeepTheirOrder() {
        IWiredEffect message = new WiredEffectWhisper(1, 1, BASE, "", 0, 0);
        IWiredEffect change = new WiredEffectChangeVariableValue(2, 1, BASE, "", 0, 0);
        IWiredEffect first = context -> {};
        IWiredEffect second = context -> {};

        assertEquals(
                List.of(first, change, message, second),
                WiredObservedEffectOrder.apply(List.of(first, message, change, second)));
        assertEquals(List.of(first, second), WiredObservedEffectOrder.apply(List.of(first, second)));
    }

    @Test
    void giveScoreRunsAfterAControlClockSoTheScoreLandsInTheGameItStarts() {
        IWiredEffect score = new WiredEffectGiveScore(1, 1, BASE, "", 0, 0);
        IWiredEffect teamScore = new WiredEffectGiveScoreToTeam(2, 1, BASE, "", 0, 0);
        IWiredEffect clock = new WiredEffectControlClock(3, 1, BASE, "", 0, 0);
        IWiredEffect other = context -> {};

        assertEquals(
                List.of(other, clock, score, teamScore),
                WiredObservedEffectOrder.apply(List.of(score, other, teamScore, clock)));
        assertEquals(List.of(clock, other, score), WiredObservedEffectOrder.apply(List.of(clock, other, score)));
    }
}
