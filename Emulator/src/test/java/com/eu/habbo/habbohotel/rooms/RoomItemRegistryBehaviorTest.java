package com.eu.habbo.habbohotel.rooms;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RoomItemRegistryBehaviorTest {

    @Test
    void quarantineRemovesEveryWiredCategoryFromExecutableIndexes() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        RoomItemRegistry registry = new RoomItemRegistry(room);
        InteractionWiredTrigger trigger = mock(InteractionWiredTrigger.class);
        InteractionWiredEffect effect = mock(InteractionWiredEffect.class);
        InteractionWiredCondition condition = mock(InteractionWiredCondition.class);
        InteractionWiredExtra extra = mock(InteractionWiredExtra.class);

        try (MockedStatic<WiredManager> wiredManager = mockStatic(WiredManager.class)) {
            registry.quarantineWired(trigger);
            registry.quarantineWired(effect);
            registry.quarantineWired(condition);
            registry.quarantineWired(extra);

            verify(specialTypes).removeTrigger(trigger);
            verify(specialTypes).removeEffect(effect);
            verify(specialTypes).removeCondition(condition);
            verify(specialTypes).removeExtra(extra);
            wiredManager.verify(() -> WiredManager.invalidateRoom(room), org.mockito.Mockito.times(4));
        }
    }

    @Test
    void rollerRegistrationAndRemovalUseTheSameSpecialTypeRegistry() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(room.getFurniVariableManager()).thenReturn(furniVariables);

        RoomItemManager manager = new RoomItemManager(room);
        manager.getFurniOwnerNames().put(7, "owner");
        InteractionRoller roller = mock(InteractionRoller.class);
        when(roller.getId()).thenReturn(1001);
        when(roller.getUserId()).thenReturn(7);

        try (MockedStatic<BuildersClubRoomSupport> buildersClub = mockStatic(BuildersClubRoomSupport.class)) {
            buildersClub.when(() -> BuildersClubRoomSupport.isTrackedItem(1001)).thenReturn(false);
            manager.addHabboItem(roller);
            manager.removeHabboItem(roller);
        }

        verify(specialTypes).addRoller(roller);
        verify(specialTypes).removeRoller(roller);
        verify(furniVariables).removeAssignmentsForFurni(1001);
    }
}
