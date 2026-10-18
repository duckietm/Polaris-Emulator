package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Bot Teleport takes the same grid steps as a user's normal Teleport To. */
class WiredEffectBotTeleportGridTest {

    @Test
    void aBotTeleportsLikeANormalTeleportTo() {
        RoomUnit bot = mock(RoomUnit.class);
        RoomTile tile = mock(RoomTile.class);

        try (MockedStatic<WiredEffectTeleport> teleport = mockStatic(WiredEffectTeleport.class)) {
            WiredEffectBotTeleport.teleportUnitToTile(bot, tile);
            teleport.verify(() -> WiredEffectTeleport.teleportUnitToTile(bot, tile, false));
        }
    }
}
