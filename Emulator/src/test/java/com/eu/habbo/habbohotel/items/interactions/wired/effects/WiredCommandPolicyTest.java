package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.commands.SitCommand;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.api.Test;

class WiredCommandPolicyTest {

    @Test
    void commandKeyParsingDoesNotIncludeArguments() {
        assertEquals("ban", WiredCommandPolicy.commandKey("  :BAN target reason with secrets  "));
        assertEquals("", WiredCommandPolicy.commandKey("   :   "));
    }

    @Test
    void safeAllowlistIsBasedOnReviewedCommandTypes() {
        assertTrue(WiredCommandPolicy.isSafeCommandType(SitCommand.class));
        assertFalse(WiredCommandPolicy.isSafeCommandType(TestCommand.class));
    }

    @Test
    void unknownOrPrivilegedCommandRequiresPrincipalSuperwiredAndCommandPermission() {
        Command privileged = new TestCommand("cmd_ban");
        Habbo normalPrincipal = mock(Habbo.class);
        when(normalPrincipal.hasPermission("cmd_ban")).thenReturn(true);

        assertFalse(WiredCommandPolicy.canUse(privileged, null));
        assertFalse(WiredCommandPolicy.canUse(privileged, normalPrincipal));

        Habbo superwiredWithoutCommand = mock(Habbo.class);
        when(superwiredWithoutCommand.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(true);
        assertFalse(WiredCommandPolicy.canUse(privileged, superwiredWithoutCommand));

        Habbo authorizedPrincipal = mock(Habbo.class);
        when(authorizedPrincipal.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(true);
        when(authorizedPrincipal.hasPermission("cmd_ban")).thenReturn(true);
        assertTrue(WiredCommandPolicy.canUse(privileged, authorizedPrincipal));
    }

    @Test
    void targetAuthorityIsNotAnInputToTheDecision() {
        Command privileged = new TestCommand("cmd_shutdown");
        Habbo unprivilegedPrincipal = mock(Habbo.class);
        when(unprivilegedPrincipal.hasPermission("cmd_shutdown")).thenReturn(false);

        assertFalse(WiredCommandPolicy.canUse(privileged, unprivilegedPrincipal));
    }

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
