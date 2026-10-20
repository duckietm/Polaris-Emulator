package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A signalled or called stack works in a scope inside its sender's context: reads and changes reach the
 * variable where it lives, a give creates it in the child, and the sender's placeholders come after the
 * receiver's own.
 */
class WiredContextVariableScopeChainTest {

    private static Room room() {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(Set.of());
        return room;
    }

    private static WiredContext receiver(Room room, WiredContextVariableScope sender, Map<String, String> carried) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                        .contextVariableScope(sender)
                        .carriedPlaceholders(carried)
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
    }

    @Test
    void aReceiverReadsAndChangesTheSendersVariable() {
        WiredContextVariableScope sender = new WiredContextVariableScope();
        sender.assignValue(7, 1, false);

        WiredContext first = receiver(room(), sender, Map.of());
        WiredContext second = receiver(room(), sender, Map.of());

        assertTrue(first.contextVariables().hasVariable(7));
        assertTrue(first.contextVariables().updateValue(7, 5));
        // The change lives where the variable does, so the sender and every sibling see it.
        assertEquals(5, sender.getValue(7));
        assertEquals(5, second.contextVariables().getValue(7));
    }

    @Test
    void aGiveCreatesTheVariableInTheChildWhereItHidesTheSenders() {
        WiredContextVariableScope sender = new WiredContextVariableScope();
        sender.assignValue(7, 1, false);
        WiredContextVariableScope child = sender.child();

        // A plain give of a variable the chain already holds is refused.
        assertFalse(child.assignValue(7, 9, false));
        assertEquals(1, sender.getValue(7));

        // Overriding gives the child its own; the sender keeps its value.
        assertTrue(child.assignValue(7, 9, true));
        assertEquals(9, child.getValue(7));
        assertEquals(1, sender.getValue(7));
        assertTrue(child.updateValue(7, 10));
        assertEquals(1, sender.getValue(7));

        // A new variable given in the child stays the child's.
        assertTrue(child.assignValue(8, 3, false));
        assertFalse(sender.hasVariable(8));

        // Removing the child's own uncovers the sender's again; removing again reaches the sender.
        assertTrue(child.removeValue(7));
        assertEquals(1, child.getValue(7));
        assertTrue(child.removeValue(7));
        assertFalse(sender.hasVariable(7));
    }

    @Test
    void aCopyIsDetachedAndSeesTheWholeChainNearestFirst() {
        WiredContextVariableScope sender = new WiredContextVariableScope();
        sender.assignValue(7, 1, false);
        sender.assignValue(8, 2, false);
        WiredContextVariableScope child = sender.child();
        child.assignValue(7, 9, true);

        WiredContextVariableScope copy = child.copy();
        assertNull(copy.parent());
        assertEquals(9, copy.getValue(7));
        assertEquals(2, copy.getValue(8));

        copy.updateValue(8, 50);
        assertEquals(2, sender.getValue(8));
    }

    @Test
    void theSendersPlaceholdersComeAfterTheReceiversOwn() throws Exception {
        Room room = room();
        assertEquals(
                "from Alice",
                WiredTextPlaceholderUtil.applyUsernamePlaceholders(
                        receiver(room, null, Map.of("$(who)", "Alice")), "from $(who)"));

        // The receiver's own username output with the same token wins.
        WiredExtraTextOutputUsername local = new WiredExtraTextOutputUsername(20, 1, mock(Item.class), "", 0, 0);
        local.saveData(
                new WiredSettings(new int[] {1, WiredSourceUtil.SOURCE_TRIGGER}, "who\t, ", new int[0], 0), null);
        when(room.getRoomSpecialTypes().getExtras(0, 0))
                .thenReturn(new LinkedHashSet<InteractionWiredExtra>(List.of(local)));
        WiredContext ownStack = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                        .carriedPlaceholders(Map.of("$(who)", "Alice", "$(where)", "lobby"))
                        .build(),
                mock(HabboItem.class),
                mock(WiredServices.class),
                new WiredState(100));
        assertEquals(
                "Hi [] in lobby",
                WiredTextPlaceholderUtil.applyUsernamePlaceholders(ownStack, "Hi [$(who)] in $(where)"));

        // What a stack hands on: its own placeholders as they read now, then what it was handed.
        Map<String, String> handedOn = WiredTextPlaceholderUtil.carriedPlaceholders(ownStack);
        assertEquals(List.of("$(who)", "$(where)"), List.copyOf(handedOn.keySet()));
        assertEquals("", handedOn.get("$(who)"));
        assertEquals("lobby", handedOn.get("$(where)"));
    }
}
