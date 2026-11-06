package polaris.hotel.rooms;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.rooms.variables.RoomArrayVariableManager;
import polaris.hotel.rooms.variables.RoomFurniVariableManager;
import polaris.hotel.wired.arrays.WiredArrayVariableType;
import polaris.hotel.wired.core.WiredManager;

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
        RoomArrayVariableManager arrayVariables = mock(RoomArrayVariableManager.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(room.getFurniVariableManager()).thenReturn(furniVariables);
        when(room.getArrayVariableManager()).thenReturn(arrayVariables);

        RoomItemManager manager = new RoomItemManager(room);
        manager.getFurniOwnerNames().put(7, "owner");
        InteractionRoller roller = mock(InteractionRoller.class);
        when(roller.getId()).thenReturn(1001);
        when(roller.getUserId()).thenReturn(7);

        try (MockedStatic<BuildersClubRoomSupport> buildersClub = mockStatic(BuildersClubRoomSupport.class)) {
            buildersClub.when(() -> BuildersClubRoomSupport.isTrackedItem(1001)).thenReturn(false);
            manager.addUserItem(roller);
            manager.removeUserItem(roller);
        }

        verify(specialTypes).addRoller(roller);
        verify(specialTypes).removeRoller(roller);
        verify(furniVariables).removeAssignmentsForFurni(1001);
        verify(arrayVariables).removeOwner(WiredArrayVariableType.FURNI.code(), 1001);
    }
}
