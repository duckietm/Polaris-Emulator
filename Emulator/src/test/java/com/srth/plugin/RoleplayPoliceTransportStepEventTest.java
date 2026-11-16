package com.srth.plugin;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.srth.plugin.Rooms.RoomsManager;
import com.srth.plugin.habbo.Delitos.PoliceTransportManager;
import com.srth.plugin.habbo.Shooter.ShooterManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RoleplayPoliceTransportStepEventTest {

    @Test
    void forwardsPoliceStepEventToPoliceTransportManager() {
        RoomsManager roomsManager = mock(RoomsManager.class);
        RoleplayWebsocketsRoomsInfo listener = mock(RoleplayWebsocketsRoomsInfo.class, CALLS_REAL_METHODS);
        UserTakeStepEvent event = new UserTakeStepEvent(
                mock(Habbo.class),
                new RoomTile((short) 2, (short) 2, (short) 0, RoomTileState.OPEN, true),
                new RoomTile((short) 3, (short) 2, (short) 0, RoomTileState.OPEN, true));

        try (MockedStatic<RoleplayWebsocketsRoomsInfo> roleplay =
                        mockStatic(RoleplayWebsocketsRoomsInfo.class, CALLS_REAL_METHODS);
                MockedStatic<ShooterManager> shooter = mockStatic(ShooterManager.class);
                MockedStatic<PoliceTransportManager> policeTransport = mockStatic(PoliceTransportManager.class)) {
            roleplay.when(RoleplayWebsocketsRoomsInfo::getRoomsRoleplayManager).thenReturn(roomsManager);

            listener.onStepEvent(event);

            policeTransport.verify(() -> PoliceTransportManager.onPoliceStep(event));
        }
    }
}
