package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.config;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.user;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * "Bot follows user" hands each bot one user in turn and stops without needing a user, and it saves
 * the user source the way its load normalises it, so a save and a reload agree.
 */
class WiredEffectBotFollowUserTest {

    @Test
    void eachBotFollowsOneUserHandedOutInTurn() throws Exception {
        Room room = mock(Room.class);
        RoomUnit first = mock(RoomUnit.class);
        RoomUnit second = mock(RoomUnit.class);
        User firstUser = user("first", first);
        User secondUser = user("second", second);
        when(room.getUser(first)).thenReturn(firstUser);
        when(room.getUser(second)).thenReturn(secondUser);
        Bot frank = mock(Bot.class);
        Bot joe = mock(Bot.class);
        when(room.getBots("Frank")).thenReturn(List.of(frank, joe));

        WiredEffectBotFollowUser box = new WiredEffectBotFollowUser(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"bot_name\":\"Frank\",\"mode\":1,\"delay\":0,\"userSource\":0,\"botSource\":100}"), room);

        WiredContext ctx = context(room);

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER))
                    .thenReturn(List.of(first, second));

            box.execute(ctx);
        }

        // Every bot used to be told to follow every user, so all of them ended up on the last one.
        verify(frank).startFollowingUser(firstUser);
        verify(joe).startFollowingUser(secondUser);
        verify(frank, never()).startFollowingUser(secondUser);
    }

    @Test
    void stoppingNeedsNoUser() throws Exception {
        Room room = mock(Room.class);
        Bot frank = mock(Bot.class);
        when(room.getBots("Frank")).thenReturn(List.of(frank));

        WiredEffectBotFollowUser box = new WiredEffectBotFollowUser(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"bot_name\":\"Frank\",\"mode\":0,\"delay\":0,\"userSource\":0,\"botSource\":100}"), room);

        WiredContext ctx = context(room);

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER))
                    .thenReturn(List.of());

            box.execute(ctx);
        }

        verify(frank).stopFollowingUser();
    }

    @Test
    void aUserSourceThatNamesNothingIsSavedAsTheTrigger() throws Exception {
        WiredEffectBotFollowUser box = new WiredEffectBotFollowUser(1, 1, base(), "", 0, 0);

        ConfigurationManager config = config();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);

            box.saveData(new WiredSettings(new int[] {1, 999, 100}, "Frank", new int[0], 0), null);
        }

        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, json(box).get("userSource").getAsInt());
    }
}
