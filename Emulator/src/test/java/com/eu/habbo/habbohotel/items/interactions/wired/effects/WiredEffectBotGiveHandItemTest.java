package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.habbo;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** AIR's Bot Give Hand Item: bot i serves user i, hands over next to them, keeps the item for one it cannot reach. */
class WiredEffectBotGiveHandItemTest {

    @Test
    void withNoBotTheUsersGetTheItemAtOnce() throws Exception {
        Room room = mock(Room.class);
        RoomUnit user = unit(3, 3);
        Habbo habbo = habbo("user", user);
        when(room.getHabbo(user)).thenReturn(habbo);
        when(room.getBots("Frank")).thenReturn(List.of());
        WiredEffectBotGiveHandItem box = box(room);
        WiredContext ctx = context(room);

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER))
                    .thenReturn(List.of(user));
            box.execute(ctx);
        }

        assertEquals(3, user.getHandItem());
    }

    @Test
    void aBotNextToTheUserHandsItOverAtOnce() {
        Room room = openRoom();
        RoomUnit bot = unit(4, 4);
        RoomUnit user = unit(5, 5);

        BotHandItemDelivery delivery = new BotHandItemDelivery(room, bot, user, 7, new Random(1), 0L);

        assertFalse(delivery.begin());
        assertEquals(7, user.getHandItem());
        assertEquals(0, bot.getHandItem());
    }

    @Test
    void aBoxedInUserIsNotServedAndTheBotKeepsTheItem() {
        Room room = openRoom();
        when(room.getRoomUnitsAt(any())).thenReturn(List.of(mock(RoomUnit.class)));
        RoomUnit bot = unit(1, 1);
        RoomUnit user = unit(6, 6);

        BotHandItemDelivery delivery = new BotHandItemDelivery(room, bot, user, 7, new Random(1), 0L);

        assertFalse(delivery.begin());
        assertEquals(7, bot.getHandItem());
        assertEquals(0, user.getHandItem());
    }

    @Test
    void theHandOverComes300MsAfterTheBotGotNextToTheUser() {
        Room room = openRoom();
        RoomUnit bot = unit(4, 4);
        RoomUnit user = unit(5, 5);
        bot.setRoom(room);
        user.setRoom(room);
        BotHandItemDelivery delivery = new BotHandItemDelivery(room, bot, user, 7, new Random(1), 0L);

        assertTrue(delivery.tick(1_000L));
        assertTrue(delivery.tick(1_299L));
        assertEquals(0, user.getHandItem());
        assertFalse(delivery.tick(1_300L));
        assertEquals(7, user.getHandItem());
    }

    @Test
    void theFewestStepsThenTheNearestTileIsTaken() {
        Room room = openRoom();
        RoomUnit bot = unit(5, 1);
        RoomTile userTile = new RoomTile((short) 5, (short) 5, (short) 0, RoomTileState.OPEN, true);
        Set<String> picked = new HashSet<>();
        Random random = new Random(3);

        for (int i = 0; i < 40; i++) {
            RoomTile tile = BotHandItemDelivery.bestTileNextTo(room, userTile, bot, random);
            // The three tiles on the bot's side are each 3 steps away; the middle one is nearest.
            assertEquals(4, tile.y);
            picked.add(tile.x + "," + tile.y);
        }

        assertEquals(Set.of("5,4"), picked);
        assertNull(BotHandItemDelivery.bestTileNextTo(room, null, bot, random));
    }

    private static Room openRoom() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile(anyShort(), anyShort()))
                .thenAnswer(invocation -> new RoomTile(
                        invocation.getArgument(0), invocation.getArgument(1), (short) 0, RoomTileState.OPEN, true));
        when(room.getRoomUnitsAt(any())).thenReturn(List.of());
        return room;
    }

    private static RoomUnit unit(int x, int y) {
        RoomUnit unit = new RoomUnit();
        unit.setLocation(new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true));
        unit.setInRoom(true);
        return unit;
    }

    private static WiredEffectBotGiveHandItem box(Room room) throws Exception {
        WiredEffectBotGiveHandItem box = new WiredEffectBotGiveHandItem(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"bot_name\":\"Frank\",\"item_id\":3,\"delay\":0,\"userSource\":0,\"botSource\":100}"), room);
        return box;
    }

    @SuppressWarnings("unused")
    private static Bot bot() {
        Bot bot = mock(Bot.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(bot.getRoomUnit()).thenReturn(unit);
        return bot;
    }
}
