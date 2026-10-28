package com.eu.habbo.habbohotel.games.battlebanzai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTile;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredBlob;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The game kept every tile it had ever reset and never let one go: a tile picked up from the room was still part
 * of the field, and stopping the game rewrote its state and pushed an item update for something in an inventory.
 */
class BattleBanzaiGameTilesTest {

    private static InteractionBattleBanzaiTile tile(int id, int roomId) {
        InteractionBattleBanzaiTile tile = mock(InteractionBattleBanzaiTile.class);
        when(tile.getId()).thenReturn(id);
        when(tile.getRoomId()).thenReturn(roomId);
        when(tile.getExtradata()).thenReturn("1");
        return tile;
    }

    @Test
    void aTileThatLeftTheRoomIsNoLongerPartOfTheGame() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);

        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getItemsOfType(WiredBlob.class)).thenReturn(new HashSet<>());
        when(specialTypes.getGameTimers()).thenReturn(Map.of());
        when(specialTypes.getBattleBanzaiGates()).thenReturn(Map.of());
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(5);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        InteractionBattleBanzaiTile staying = tile(1, 5);
        InteractionBattleBanzaiTile pickedUp = tile(2, 0);
        Set<HabboItem> floor = new HashSet<>(Set.of(staying, pickedUp));
        when(room.getFloorItems()).thenReturn(floor);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getThreading).thenReturn(mock(ThreadPooling.class));
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(0);

            BattleBanzaiGame game = new BattleBanzaiGame(room);
            game.resetMap();

            // The tile is picked up between two games: the next reset no longer sees it.
            floor.remove(pickedUp);
            game.resetMap();
            game.stop();
        }

        verify(staying).setExtradata("0");
        verify(room).updateItem(staying);
        verify(pickedUp, never()).setExtradata("0");
        verify(room, never()).updateItem(pickedUp);
    }

    @Test
    void stoppingLeavesATilePickedUpDuringTheGameAlone() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);

        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getItemsOfType(WiredBlob.class)).thenReturn(new HashSet<>());
        when(specialTypes.getGameTimers()).thenReturn(Map.of());
        when(specialTypes.getBattleBanzaiGates()).thenReturn(Map.of());
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(5);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        InteractionBattleBanzaiTile tile = tile(3, 5);
        when(room.getFloorItems()).thenReturn(new HashSet<>(Set.of(tile)));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getThreading).thenReturn(mock(ThreadPooling.class));
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(0);

            BattleBanzaiGame game = new BattleBanzaiGame(room);
            game.resetMap();

            // Picked up while the game runs: it now belongs to no room.
            when(tile.getRoomId()).thenReturn(0);
            game.stop();
        }

        verify(tile, never()).setExtradata("0");
        verify(room, never()).updateItem(tile);
    }
}
