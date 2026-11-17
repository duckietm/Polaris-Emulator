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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
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
                        "Acciones",
                        List.of(new RoleplayAvatarMenuManager.Button("Acción propia", "taxi_custom", true))));
        assertEquals("Taxista", manager.getBotMenu(bot).title());
        manager.registerBotContextMenu(
                "Taxista",
                new RoleplayMenu(
                        "Taxi",
                        List.of(new RoleplayMenu.Button(
                                "Vehículo",
                                "taxi_vehicle",
                                true,
                                new RoleplayMenu(
                                        "Vehículo",
                                        List.of(new RoleplayMenu.Button("Acción propia", "taxi_custom", true)))))));

        assertEquals("Taxista", manager.getBotContextMenu(bot).title());
        assertTrue(manager.executeBotAction(null, bot, "taxi_custom"));
        assertFalse(manager.executeBotAction(null, bot, "taxi_vehicle"));
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

    @Test
    void policeActionsAreGroupedUnderThePoliceSubmenu() {
        JsonArray actions = new JsonArray();
        actions.add(action("Revisar pertenencias", "UserActionRevisarPertenencias", true));
        actions.add(action("Arrestar al usuario", "UserActionArrestar", false));

        RoleplayMenu menu = RoleplayAvatarMenuManager.buildUserMenu("Ciudadano", actions);

        assertEquals(1, menu.buttons().size());
        assertEquals("Policía", menu.buttons().get(0).label());
        assertTrue(menu.buttons().get(0).submenu().containsEnabledAction("UserActionRevisarPertenencias"));
        assertFalse(menu.buttons().get(0).submenu().containsEnabledAction("UserActionArrestar"));
    }

    private static JsonObject action(String label, String actionId, boolean enabled) {
        JsonObject action = new JsonObject();
        action.addProperty("use_btn_name", label);
        action.addProperty("event", actionId);
        action.addProperty("unlock", enabled ? 1 : 0);
        return action;
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
