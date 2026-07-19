package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomItemRegistryBehaviorTest {

    @Test
    void rollerRegistrationAndRemovalUseTheSameSpecialTypeRegistry() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        RoomFurniVariableManager furniVariables =
                mock(RoomFurniVariableManager.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(room.getFurniVariableManager()).thenReturn(furniVariables);

        RoomItemManager manager = new RoomItemManager(room);
        manager.getFurniOwnerNames().put(7, "owner");
        InteractionRoller roller = mock(InteractionRoller.class);
        when(roller.getId()).thenReturn(1001);
        when(roller.getUserId()).thenReturn(7);

        try (MockedStatic<BuildersClubRoomSupport> buildersClub =
                     mockStatic(BuildersClubRoomSupport.class)) {
            buildersClub.when(() ->
                    BuildersClubRoomSupport.isTrackedItem(1001))
                    .thenReturn(false);
            manager.addHabboItem(roller);
            manager.removeHabboItem(roller);
        }

        verify(specialTypes).addRoller(roller);
        verify(specialTypes).removeRoller(roller);
        verify(furniVariables).removeAssignmentsForFurni(1001);
    }
}
