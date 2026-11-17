package com.srth.plugin.ActionUsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RoleplayAvatarMenuManagerTest {
    @Test
    void defaultBotMenuGreetsTheVisitor() {
        RoleplayAvatarMenuManager manager = new RoleplayAvatarMenuManager();
        TrackingBot bot = new TrackingBot(37, "Taxi");

        RoleplayAvatarMenuManager.Menu menu = manager.getBotMenu(bot);

        assertEquals("Taxi", menu.title());
        assertEquals(1, menu.buttons().size());
        assertEquals("Saludar", menu.buttons().get(0).label());
        assertTrue(manager.executeBotAction(null, bot, "bot_greet"));
        assertEquals("¡Hola!", bot.spokenMessage);
    }

    @Test
    void botActionsMustBeRegisteredAndListedForThatBot() {
        RoleplayAvatarMenuManager manager = new RoleplayAvatarMenuManager();
        Bot bot = new Bot(42, "Taxista", "", "", HabboGender.M, 1, "owner");
        AtomicInteger executions = new AtomicInteger();
        manager.registerBotAction("taxi_custom", (Habbo viewer, Bot target) -> executions.incrementAndGet());
        manager.registerBotMenu(
                "Taxista",
                new RoleplayAvatarMenuManager.Menu(
                        "Taxi", List.of(new RoleplayAvatarMenuManager.Button("Acción propia", "taxi_custom", true))));

        assertEquals("Taxista", manager.getBotMenu(bot).title());
        assertTrue(manager.executeBotAction(null, bot, "taxi_custom"));
        assertFalse(manager.executeBotAction(null, bot, "bot_greet"));
        assertEquals(1, executions.get());
    }

    @Test
    void userMenuUsesTheTargetUsernameAsTitle() throws Exception {
        RoleplayAvatarMenuManager manager = new RoleplayAvatarMenuManager();
        Habbo target = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(target.getHabboInfo()).thenReturn(info);
        when(info.getUsername()).thenReturn("Ciudadano");

        RoleplayAvatarMenuManager.Menu menu = manager.getUserMenu(target, null);

        assertEquals("Ciudadano", menu.title());
        assertTrue(menu.buttons().isEmpty());
    }

    private static final class TrackingBot extends Bot {
        private String spokenMessage;

        private TrackingBot(int id, String name) {
            super(id, name, "", "", HabboGender.M, 0, "");
        }

        @Override
        public void talk(String message) {
            this.spokenMessage = message;
        }
    }
}
