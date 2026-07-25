package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.google.gson.JsonParser;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WiredEffectSayCommandSecurityTest {

    @Test
    void normalUserCannotSavePrivilegedCommandEvenWhenTargetCouldRunIt() {
        Fixture fixture = fixture(false, true);

        try (MockedStatic<CommandHandler> commands = mockStatic(CommandHandler.class)) {
            commands.when(() -> CommandHandler.getCommand("ban")).thenReturn(new TestCommand("cmd_ban"));

            assertFalse(fixture.effect.saveData(settings(":ban target secret"), fixture.client));
        }
    }

    @Test
    void authorizedPrincipalIsPersistedWhenPrivilegedCommandIsSaved() {
        Fixture fixture = fixture(true, true);

        try (MockedStatic<CommandHandler> commands = mockStatic(CommandHandler.class)) {
            commands.when(() -> CommandHandler.getCommand("ban")).thenReturn(new TestCommand("cmd_ban"));

            assertTrue(fixture.effect.saveData(settings(":ban target reason"), fixture.client));
        }

        var data = JsonParser.parseString(fixture.effect.getWiredData()).getAsJsonObject();
        assertEquals(42, data.get("configuredByUserId").getAsInt());
        assertEquals(":ban target reason", data.get("command").getAsString());
    }

    @Test
    void legacySavedCommandFallsBackToItemOwnerAsPrincipal() throws Exception {
        Item baseItem = mock(Item.class);
        WiredEffectSayCommand effect = new WiredEffectSayCommand(55, 7, baseItem, "0", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn("{\"command\":\":sit\",\"delay\":2,\"userSource\":0}");

        effect.loadWiredData(row, null);

        var data = JsonParser.parseString(effect.getWiredData()).getAsJsonObject();
        assertEquals(7, data.get("configuredByUserId").getAsInt());
        assertEquals(":sit", data.get("command").getAsString());
        assertEquals(2, data.get("delay").getAsInt());
    }

    private static WiredSettings settings(String command) {
        return new WiredSettings(new int[] {0}, command, new int[0], -1, 3);
    }

    private static Fixture fixture(boolean superwired, boolean commandPermission) {
        Item baseItem = mock(Item.class);
        WiredEffectSayCommand effect = new WiredEffectSayCommand(55, 7, baseItem, "0", 0, 0);
        GameClient client = mock(GameClient.class);
        Habbo principal = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);

        when(client.getHabbo()).thenReturn(principal);
        when(principal.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(42);
        when(principal.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(superwired);
        when(principal.hasPermission("cmd_ban")).thenReturn(commandPermission);

        return new Fixture(effect, client);
    }

    private record Fixture(WiredEffectSayCommand effect, GameClient client) {}

    private static final class TestCommand extends Command {
        private TestCommand(String permission) {
            super(permission, new String[] {"fixture"});
        }

        @Override
        public boolean handle(GameClient gameClient, String[] params) {
            return true;
        }
    }
}
