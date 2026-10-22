package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What the wired registry of a room answers: a box is found by its id, by its type and by the tile it stands on, a move
 * re-files it, and what the lookups return is a copy the caller may change.
 */
class RoomSpecialTypesWiredRegistryTest {
    private static final WiredTriggerType TRIGGER_A = WiredTriggerType.values()[0];
    private static final WiredTriggerType TRIGGER_B = WiredTriggerType.values()[1];
    private static final WiredEffectType EFFECT_A = WiredEffectType.values()[0];
    private static final WiredEffectType EFFECT_B = WiredEffectType.values()[1];
    private static final WiredConditionType CONDITION_A = WiredConditionType.values()[0];
    private static final WiredConditionType CONDITION_B = WiredConditionType.values()[1];

    @Test
    void aTriggerIsFoundByIdTypeAndTile() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        InteractionWiredTrigger first = trigger(1, TRIGGER_A, 3, 4);
        InteractionWiredTrigger second = trigger(2, TRIGGER_B, 3, 4);
        InteractionWiredTrigger third = trigger(3, TRIGGER_A, 5, 6);
        types.addTrigger(first);
        types.addTrigger(second);
        types.addTrigger(third);

        assertSame(second, types.getTrigger(2));
        assertNull(types.getTrigger(99));
        assertEquals(Set.of(first, second, third), types.getTriggers());
        assertEquals(Set.of(first, third), types.getTriggers(TRIGGER_A));
        assertEquals(Set.of(first, second), types.getTriggers(3, 4));
        assertTrue(types.getTriggers(7, 7).isEmpty());
        assertSame(first, types.getSpecialItem(1));
    }

    @Test
    void movingATriggerFilesItAtItsNewTile() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        InteractionWiredTrigger trigger = trigger(1, TRIGGER_A, 3, 4);
        types.addTrigger(trigger);

        when(trigger.getX()).thenReturn((short) 8);
        when(trigger.getY()).thenReturn((short) 9);
        types.updateTriggerLocation(trigger, 3, 4);

        assertTrue(types.getTriggers(3, 4).isEmpty());
        assertEquals(Set.of(trigger), types.getTriggers(8, 9));
        assertEquals(Set.of(trigger), types.getTriggers(TRIGGER_A), "its type does not change with the tile");
    }

    @Test
    void removingATriggerLeavesNoTraceOfIt() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        InteractionWiredTrigger trigger = trigger(1, TRIGGER_A, 3, 4);
        types.addTrigger(trigger);

        types.removeTrigger(trigger);

        assertNull(types.getTrigger(1));
        assertTrue(types.getTriggers().isEmpty());
        assertTrue(types.getTriggers(TRIGGER_A).isEmpty());
        assertTrue(types.getTriggers(3, 4).isEmpty());
        assertNull(types.getSpecialItem(1));
    }

    @Test
    void whatALookupReturnsIsACopyTheCallerMayChange() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        InteractionWiredTrigger trigger = trigger(1, TRIGGER_A, 3, 4);
        types.addTrigger(trigger);

        types.getTriggers().clear();
        types.getTriggers(TRIGGER_A).clear();
        types.getTriggers(3, 4).clear();

        assertEquals(Set.of(trigger), types.getTriggers());
        assertEquals(Set.of(trigger), types.getTriggers(TRIGGER_A));
        assertEquals(Set.of(trigger), types.getTriggers(3, 4));
    }

    @Test
    void anEffectFollowsTheSameRules() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        InteractionWiredEffect first = effect(1, EFFECT_A, 3, 4);
        InteractionWiredEffect second = effect(2, EFFECT_B, 3, 4);
        types.addEffect(first);
        types.addEffect(second);

        assertSame(first, types.getEffect(1));
        assertEquals(Set.of(first, second), types.getEffects());
        assertEquals(Set.of(second), types.getEffects(EFFECT_B));
        assertEquals(Set.of(first, second), types.getEffects(3, 4));

        when(first.getX()).thenReturn((short) 1);
        when(first.getY()).thenReturn((short) 1);
        types.updateEffectLocation(first, 3, 4);
        assertEquals(Set.of(second), types.getEffects(3, 4));
        assertEquals(Set.of(first), types.getEffects(1, 1));

        types.removeEffect(first);
        assertNull(types.getEffect(1));
        assertNull(types.getSpecialItem(1));
        assertTrue(types.getEffects(1, 1).isEmpty());
        assertEquals(Set.of(second), types.getEffects());
    }

    @Test
    void aConditionFollowsTheSameRules() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        InteractionWiredCondition first = condition(1, CONDITION_A, 3, 4);
        InteractionWiredCondition second = condition(2, CONDITION_B, 5, 5);
        types.addCondition(first);
        types.addCondition(second);

        assertSame(second, types.getCondition(2));
        assertEquals(Set.of(first, second), types.getConditions());
        assertEquals(Set.of(first), types.getConditions(CONDITION_A));
        assertEquals(Set.of(second), types.getConditions(5, 5));

        when(second.getX()).thenReturn((short) 3);
        when(second.getY()).thenReturn((short) 4);
        types.updateConditionLocation(second, 5, 5);
        assertEquals(Set.of(first, second), types.getConditions(3, 4));
        assertTrue(types.getConditions(5, 5).isEmpty());

        types.removeCondition(first);
        types.removeCondition(second);
        assertTrue(types.getConditions().isEmpty());
        assertTrue(types.getConditions(3, 4).isEmpty());
    }

    @Test
    void anExtraIsFoundByIdAndTileAndByItsKindAtATile() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        InteractionWiredExtra extra = extra(1, 3, 4);
        types.addExtra(extra);

        assertSame(extra, types.getExtra(1));
        assertEquals(Set.of(extra), types.getExtras());
        assertEquals(Set.of(extra), types.getExtras(3, 4));
        assertTrue(types.hasExtraType((short) 3, (short) 4, InteractionWiredExtra.class));
        assertFalse(types.hasExtraType((short) 3, (short) 4, WiredExtraTextOutputUsername.class));
        assertFalse(types.hasExtraType((short) 9, (short) 9, InteractionWiredExtra.class));

        when(extra.getX()).thenReturn((short) 6);
        when(extra.getY()).thenReturn((short) 6);
        types.updateExtraLocation(extra, 3, 4);
        assertTrue(types.getExtras(3, 4).isEmpty());
        assertEquals(Set.of(extra), types.getExtras(6, 6));

        types.removeExtra(extra);
        assertNull(types.getExtra(1));
        assertNull(types.getSpecialItem(1));
        assertTrue(types.getExtras().isEmpty());
    }

    @Test
    void disposingTheRoomEmptiesTheRegistry() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        types.addTrigger(trigger(1, TRIGGER_A, 3, 4));
        types.addEffect(effect(2, EFFECT_A, 3, 4));
        types.addCondition(condition(3, CONDITION_A, 3, 4));
        types.addExtra(extra(4, 3, 4));

        types.dispose();

        assertTrue(types.getTriggers().isEmpty());
        assertTrue(types.getEffects().isEmpty());
        assertTrue(types.getConditions().isEmpty());
        assertTrue(types.getExtras().isEmpty());
        assertNull(types.getSpecialItem(1));
    }

    private static InteractionWiredTrigger trigger(int id, WiredTriggerType type, int x, int y) {
        InteractionWiredTrigger box = mock(InteractionWiredTrigger.class);
        when(box.getId()).thenReturn(id);
        when(box.getType()).thenReturn(type);
        when(box.getX()).thenReturn((short) x);
        when(box.getY()).thenReturn((short) y);
        return box;
    }

    private static InteractionWiredEffect effect(int id, WiredEffectType type, int x, int y) {
        InteractionWiredEffect box = mock(InteractionWiredEffect.class);
        when(box.getId()).thenReturn(id);
        when(box.getType()).thenReturn(type);
        when(box.getX()).thenReturn((short) x);
        when(box.getY()).thenReturn((short) y);
        return box;
    }

    private static InteractionWiredCondition condition(int id, WiredConditionType type, int x, int y) {
        InteractionWiredCondition box = mock(InteractionWiredCondition.class);
        when(box.getId()).thenReturn(id);
        when(box.getType()).thenReturn(type);
        when(box.getX()).thenReturn((short) x);
        when(box.getY()).thenReturn((short) y);
        return box;
    }

    private static InteractionWiredExtra extra(int id, int x, int y) {
        InteractionWiredExtra box = mock(InteractionWiredExtra.class);
        when(box.getId()).thenReturn(id);
        when(box.getX()).thenReturn((short) x);
        when(box.getY()).thenReturn((short) y);
        return box;
    }
}
