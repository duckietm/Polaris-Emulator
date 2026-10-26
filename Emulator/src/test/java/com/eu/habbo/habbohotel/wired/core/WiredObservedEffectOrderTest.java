package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAdjustClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectControlClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniToUser;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScore;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScoreToTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRelativeMove;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectUserToFurni;
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

    @Test
    void aSetClockRunsAfterTheAdjustClocksThatStepTheClock() throws Exception {
        WiredEffectAdjustClock set = adjust(1, 2);
        WiredEffectAdjustClock decrease = adjust(2, 1);
        WiredEffectAdjustClock increase = adjust(3, 0);
        WiredEffectAdjustClock secondSet = adjust(4, 2);

        assertEquals(List.of(decrease, set), WiredObservedEffectOrder.apply(List.of(set, decrease)));
        assertEquals(List.of(decrease, set), WiredObservedEffectOrder.apply(List.of(decrease, set)));
        assertEquals(
                List.of(increase, decrease, set, secondSet),
                WiredObservedEffectOrder.apply(List.of(set, increase, secondSet, decrease)));
        assertEquals(List.of(set, secondSet), WiredObservedEffectOrder.apply(List.of(set, secondSet)));
    }

    @Test
    void variableWritesRunBehindTheMovesTheyReadAndRemoveBehindGive() {
        IWiredEffect remove = new WiredEffectRemoveVariable(1, 1, BASE, "", 0, 0);
        IWiredEffect give = new WiredEffectGiveVariable(2, 1, BASE, "", 0, 0);
        IWiredEffect toggle = new WiredEffectToggleFurni(3, 1, BASE, "", 0, 0);
        IWiredEffect change = new WiredEffectChangeVariableValue(4, 1, BASE, "", 0, 0);
        IWiredEffect furniToUser = new WiredEffectFurniToUser(5, 1, BASE, "", 0, 0);
        IWiredEffect userToFurni = new WiredEffectUserToFurni(6, 1, BASE, "", 0, 0);
        IWiredEffect moveRotate = new WiredEffectMoveRotateFurni(7, 1, BASE, "", 0, 0);

        assertEquals(List.of(give, remove), WiredObservedEffectOrder.apply(List.of(remove, give)));
        // A chain: Remove behind Give, Give behind Toggle, settled over several passes.
        assertEquals(List.of(toggle, give, remove), WiredObservedEffectOrder.apply(List.of(remove, give, toggle)));
        assertEquals(List.of(furniToUser, change), WiredObservedEffectOrder.apply(List.of(change, furniToUser)));
        assertEquals(
                List.of(moveRotate, userToFurni), WiredObservedEffectOrder.apply(List.of(userToFurni, moveRotate)));
        assertEquals(
                List.of(moveRotate, userToFurni, give),
                WiredObservedEffectOrder.apply(List.of(give, userToFurni, moveRotate)));
    }

    private static WiredEffectAdjustClock adjust(int id, int operator) throws Exception {
        WiredEffectAdjustClock effect = new WiredEffectAdjustClock(id, 1, BASE, "", 0, 0);
        java.lang.reflect.Field field = WiredEffectAdjustClock.class.getDeclaredField("operator");
        field.setAccessible(true);
        field.setInt(effect, operator);
        return effect;
    }
}
