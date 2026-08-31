package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreDataEntry;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreManager;
import com.google.gson.JsonParser;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * Until this box existed a highscore board had one writer, Game.onEnd(), reached only through a game
 * timer - so a room that scored with wired alone could put nothing on a board at all.
 */
class WiredEffectGivePointsHighscoreTest {

    @Test
    @DisplayName("every board in the room gets one entry, for the resolved users, flagged as a win")
    void writesOneEntryPerBoard() {
        WiredEffectGivePointsHighscore effect = configured(25);

        InteractionWiredHighscore boardOne = mock(InteractionWiredHighscore.class);
        InteractionWiredHighscore boardTwo = mock(InteractionWiredHighscore.class);
        when(boardOne.getId()).thenReturn(101);
        when(boardTwo.getId()).thenReturn(202);

        Room room = roomWith(boardOne, boardTwo);
        RoomUnit unit = mock(RoomUnit.class);
        // Built before the stubbing: habbo() stubs its own mocks, and Mockito reads nested
        // stubbing inside a when(...) argument as an unfinished one.
        Habbo standingThere = habbo(77);
        when(room.getHabbo(unit)).thenReturn(standingThere);

        WiredHighscoreManager manager = mock(WiredHighscoreManager.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            wire(emulator, manager);
            WiredContext ctx = context(room);
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER))
                    .thenReturn(List.of(unit));

            effect.execute(ctx);

            ArgumentCaptor<WiredHighscoreDataEntry> written = ArgumentCaptor.forClass(WiredHighscoreDataEntry.class);
            verify(manager, org.mockito.Mockito.times(2)).addHighscoreData(written.capture());

            assertEquals(
                    List.of(101, 202),
                    written.getAllValues().stream()
                            .map(WiredHighscoreDataEntry::getItemId)
                            .toList(),
                    "one entry per board, in room order");

            WiredHighscoreDataEntry first = written.getAllValues().get(0);
            assertEquals(List.of(77), first.getUserIds());
            assertEquals(25, first.getScore());
            assertTrue(first.isWin(), "a most-wins board reads one firing as one win");
        }
    }

    @Test
    @DisplayName("no users resolved means no row, rather than a blank line on the board")
    void writesNothingWhenNobodyResolves() {
        WiredEffectGivePointsHighscore effect = configured(10);

        InteractionWiredHighscore board = mock(InteractionWiredHighscore.class);
        Room room = roomWith(board);
        WiredHighscoreManager manager = mock(WiredHighscoreManager.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            wire(emulator, manager);
            WiredContext ctx = context(room);
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER))
                    .thenReturn(List.of());

            effect.execute(ctx);

            verify(manager, never()).addHighscoreData(any());
        }
    }

    @Test
    @DisplayName("an unconfigured box writes nothing even with a user standing there")
    void anUnsetAmountWritesNothing() {
        WiredEffectGivePointsHighscore effect = new WiredEffectGivePointsHighscore(1, 1, mock(Item.class), "0", 0, 0);

        InteractionWiredHighscore board = mock(InteractionWiredHighscore.class);
        Room room = roomWith(board);
        WiredHighscoreManager manager = mock(WiredHighscoreManager.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            wire(emulator, manager);

            effect.execute(context(room));

            verify(manager, never()).addHighscoreData(any());
        }
    }

    @Test
    @DisplayName("the amount survives a save and load round trip")
    void theAmountSurvivesARoundTrip() throws Exception {
        WiredEffectGivePointsHighscore saved = configured(42);

        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(saved.getWiredData());

        WiredEffectGivePointsHighscore loaded = new WiredEffectGivePointsHighscore(2, 1, mock(Item.class), "0", 0, 0);
        loaded.loadWiredData(row, null);

        assertEquals(
                42,
                JsonParser.parseString(loaded.getWiredData())
                        .getAsJsonObject()
                        .get("amount")
                        .getAsInt());
    }

    @Test
    @DisplayName("a truncated row is no configuration rather than a lost furni")
    void aTruncatedRowLoadsAsUnconfigured() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn("{\"amount\":9,\"delay\":0,\"userSo");

        WiredEffectGivePointsHighscore effect = new WiredEffectGivePointsHighscore(3, 1, mock(Item.class), "0", 0, 0);
        effect.loadWiredData(row, null);

        assertEquals(
                0,
                JsonParser.parseString(effect.getWiredData())
                        .getAsJsonObject()
                        .get("amount")
                        .getAsInt());
    }

    @Test
    @DisplayName("an amount of zero is refused at save time")
    void zeroIsRefused() {
        WiredEffectGivePointsHighscore effect = new WiredEffectGivePointsHighscore(4, 1, mock(Item.class), "0", 0, 0);

        assertFalse(effect.saveData(
                new WiredSettings(new int[] {0}, "0", new int[0], -1, 0),
                mock(com.eu.habbo.habbohotel.gameclients.GameClient.class)));
    }

    private static WiredEffectGivePointsHighscore configured(int amount) {
        WiredEffectGivePointsHighscore effect = new WiredEffectGivePointsHighscore(9, 1, mock(Item.class), "0", 0, 0);
        assertTrue(effect.saveData(
                new WiredSettings(
                        new int[] {WiredSourceUtil.SOURCE_TRIGGER}, String.valueOf(amount), new int[0], -1, 0),
                mock(com.eu.habbo.habbohotel.gameclients.GameClient.class)));
        return effect;
    }

    private static Room roomWith(InteractionWiredHighscore... boards) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        Set<HabboItem> items = new LinkedHashSet<>(List.of(boards));

        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getItemsOfType(InteractionWiredHighscore.class)).thenReturn(items);

        return room;
    }

    private static WiredContext context(Room room) {
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        return ctx;
    }

    private static Habbo habbo(int id) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private static void wire(MockedStatic<Emulator> emulator, WiredHighscoreManager manager) {
        GameEnvironment environment = mock(GameEnvironment.class);
        ItemManager itemManager = mock(ItemManager.class);

        when(environment.getItemManager()).thenReturn(itemManager);
        when(itemManager.getHighscoreManager()).thenReturn(manager);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
    }
}
