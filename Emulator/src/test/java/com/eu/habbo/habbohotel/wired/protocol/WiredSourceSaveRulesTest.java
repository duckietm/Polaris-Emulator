package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The source rules a wired save goes through before the box reads its settings. */
class WiredSourceSaveRulesTest {

    @Test
    void fillsMissingSlotsWithTheDefaults() {
        WiredSettings settings = settings(new int[] {100}, new int[0]);

        assertNull(WiredSourceSaveRules.apply(box("wf_act_send_signal"), settings, room(Set.of())));
        assertArrayEquals(new int[] {100, 200}, settings.getFurniSourceTypes());
        assertArrayEquals(new int[] {200}, settings.getUserSourceTypes());
    }

    @Test
    void dropsSlotsBeyondTheBoxCount() {
        WiredSettings settings = settings(new int[] {200, 0}, new int[] {0});

        assertNull(WiredSourceSaveRules.apply(box("wf_act_remove_furni"), settings, room(Set.of())));
        assertArrayEquals(new int[] {200}, settings.getFurniSourceTypes());
        assertArrayEquals(new int[] {0}, settings.getUserSourceTypes());
    }

    @Test
    void refusesAFurniSourceTheSlotDoesNotAllow() {
        WiredSettings settings = settings(new int[] {100}, new int[0]);

        assertEquals(
                WiredSourceSaveRules.INVALID_FURNI_SOURCE,
                WiredSourceSaveRules.apply(box("wf_act_remove_furni"), settings, room(Set.of())));
    }

    @Test
    void leavesBoxesWithoutAConfAlone() {
        WiredSettings settings = settings(new int[] {100, 101, 7}, new int[] {0, 0});

        assertNull(WiredSourceSaveRules.apply(box("wf_act_toggle_state"), settings, room(Set.of())));
        assertArrayEquals(new int[] {100, 101, 7}, settings.getFurniSourceTypes());
        assertArrayEquals(new int[] {0, 0}, settings.getUserSourceTypes());
    }

    @Test
    void acceptsTheClickedAndReachedUserOnlyUnderTheirTrigger() {
        WiredSettings clicked = settings(new int[0], new int[] {11});
        WiredSettings reached = settings(new int[0], new int[] {10});

        assertEquals(
                WiredSourceSaveRules.INVALID_USER_SOURCE,
                WiredSourceSaveRules.apply(box("wf_act_give_score"), clicked, room(Set.of())));
        assertEquals(
                WiredSourceSaveRules.INVALID_USER_SOURCE,
                WiredSourceSaveRules.apply(box("wf_act_give_score"), reached, room(Set.of())));

        assertNull(WiredSourceSaveRules.apply(
                box("wf_act_give_score"),
                settings(new int[0], new int[] {11}),
                room(Set.of(trigger("wf_trg_click_user")))));
        assertNull(WiredSourceSaveRules.apply(
                box("wf_act_kick_user"),
                settings(new int[0], new int[] {10}),
                room(Set.of(trigger("wf_trg_bot_reached_avtr")))));
    }

    @Test
    void fillOfABoxWithoutSlotsKeepsWhatItHad() {
        assertArrayEquals(new int[] {5}, WiredSourceSaveRules.fill(new int[0], 0, new int[0], new int[] {5}));
        assertArrayEquals(new int[] {100, 0}, WiredSourceSaveRules.fill(new int[0], 2, new int[] {100}, null));
    }

    private static WiredSettings settings(int[] furni, int[] users) {
        WiredSettings settings = new WiredSettings(new int[0], "", new int[0], -1);
        settings.setFurniSourceTypes(furni);
        settings.setUserSourceTypes(users);
        return settings;
    }

    private static HabboItem box(String interaction) {
        HabboItem box = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(box.getBaseItem()).thenReturn(base);
        when(base.getInteractionType()).thenReturn(new ItemInteraction(interaction, HabboItem.class));
        return box;
    }

    private static InteractionWiredTrigger trigger(String interaction) {
        InteractionWiredTrigger trigger = mock(InteractionWiredTrigger.class);
        Item base = mock(Item.class);
        when(trigger.getBaseItem()).thenReturn(base);
        when(base.getInteractionType()).thenReturn(new ItemInteraction(interaction, HabboItem.class));
        return trigger;
    }

    private static Room room(Set<InteractionWiredTrigger> triggers) {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        when(types.getTriggers(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new LinkedHashSet<>(triggers));
        return room;
    }
}
