package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.user;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.core.TextsManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;

/**
 * The official "hide the bot name" option of Bot Talk and Bot Talk To Avatar reaches the bot's chat line, so the
 * client draws the bubble without the name. A box without the option keeps the name.
 */
class WiredEffectBotTalkHideNameTest {

    @Test
    void botTalkHidesTheNameWhenTheBoxSaysSo() throws Exception {
        Room room = room();
        Bot bot = bot();
        when(room.getBots("Frank")).thenReturn(List.of(bot));

        WiredEffectBotTalk box = new WiredEffectBotTalk(1, 1, base(), "", 0, 0);
        box.loadWiredData(row(talkData(0, true)), room);
        assertTrue(box.isHideBotName());

        runStatics(context(room), null, () -> box.execute(context(room)));

        verify(bot).talk("hi", -1, true);
    }

    @Test
    void botShoutKeepsTheNameByDefault() throws Exception {
        Room room = room();
        Bot bot = bot();
        when(room.getBots("Frank")).thenReturn(List.of(bot));

        WiredEffectBotTalk box = new WiredEffectBotTalk(1, 1, base(), "", 0, 0);
        box.loadWiredData(row(talkData(1, false)), room);
        assertFalse(box.isHideBotName());

        runStatics(context(room), null, () -> box.execute(context(room)));

        verify(bot).shout("hi", -1, false);
    }

    @Test
    void botWhisperToAnAvatarHidesTheName() throws Exception {
        Room room = room();
        Bot bot = bot();
        RoomUnit unit = mock(RoomUnit.class);
        User target = user("alice", unit);
        when(room.getBots("Frank")).thenReturn(List.of(bot));
        when(room.getUser(unit)).thenReturn(target);

        WiredEffectBotTalkToUser box = new WiredEffectBotTalkToUser(1, 1, base(), "", 0, 0);
        box.loadWiredData(row(toUserData(1, true)), room);
        WiredContext ctx = context(room);

        runStatics(ctx, unit, () -> box.execute(ctx));

        verify(bot).whisper("hi", target, -1, true);
    }

    @Test
    void botTalkToAnAvatarHidesTheName() throws Exception {
        Room room = room();
        Bot bot = bot();
        RoomUnit unit = mock(RoomUnit.class);
        User target = user("alice", unit);
        when(room.getBots("Frank")).thenReturn(List.of(bot));
        when(room.getUser(unit)).thenReturn(target);

        WiredEffectBotTalkToUser box = new WiredEffectBotTalkToUser(1, 1, base(), "", 0, 0);
        box.loadWiredData(row(toUserData(0, true)), room);
        WiredContext ctx = context(room);

        runStatics(ctx, unit, () -> box.execute(ctx));

        verify(bot).talk("alice: hi", -1, true);
    }

    private static String talkData(int mode, boolean hideBotName) {
        return "{\"bot_name\":\"Frank\",\"mode\":" + mode + ",\"message\":\"hi\",\"delay\":0,\"botSource\":100,"
                + "\"bubbleWidthOverride\":-1,\"hideBotName\":" + hideBotName + "}";
    }

    private static String toUserData(int mode, boolean hideBotName) {
        return "{\"bot_name\":\"Frank\",\"mode\":" + mode + ",\"message\":\"hi\",\"delay\":0,\"userSource\":0,"
                + "\"botSource\":100,\"bubbleWidthOverride\":-1,\"hideBotName\":" + hideBotName + "}";
    }

    private static Room room() {
        Room room = mock(Room.class);
        when(room.getName()).thenReturn("room");
        when(room.getOwnerName()).thenReturn("owner");
        return room;
    }

    private static Bot bot() {
        Bot bot = mock(Bot.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(bot.getRoomUnit()).thenReturn(unit);
        when(bot.getName()).thenReturn("Frank");
        return bot;
    }

    /** Runs the box with the hotel texts falling back to their defaults and no stack listening to the bot. */
    private static void runStatics(WiredContext ctx, RoomUnit user, Runnable run) {
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        ConfigurationManager config = mock(ConfigurationManager.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredManager> wired = mockStatic(WiredManager.class);
                MockedStatic<WiredTextPlaceholderUtil> placeholders = mockStatic(WiredTextPlaceholderUtil.class);
                MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            emulator.when(Emulator::getTexts).thenReturn(texts);
            emulator.when(Emulator::getConfig).thenReturn(config);
            wired.when(() -> WiredManager.triggerUserSays(any(), any(), anyString()))
                    .thenReturn(false);
            placeholders
                    .when(() -> WiredTextPlaceholderUtil.applyUsernamePlaceholders(any(), anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(1));
            sources.when(() -> WiredSourceUtil.resolveUsers(any(WiredContext.class), anyInt()))
                    .thenReturn(user != null ? List.of(user) : List.of());

            run.run();
        }
    }
}
