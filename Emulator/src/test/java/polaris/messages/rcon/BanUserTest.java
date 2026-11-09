package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.modtool.ConsoleModeration;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.modtool.ModToolBanType;
import polaris.hotel.users.UserInfo;
import polaris.messages.command.CommandRegistry;
import polaris.messages.command.CommandResult;

/** RCON banuser and unbanuser: the All-in-1-Manager's ban buttons. */
class BanUserTest {

    private final List<String> written = new ArrayList<>();

    @AfterEach
    void tearDown() {
        ConsoleModeration.uninstall();
    }

    private CommandRegistry registry() {
        UserInfo info = mock(UserInfo.class);
        ConsoleModeration.install(new ConsoleModeration(
                id -> id == 7 ? info : null,
                id -> null,
                (id, seconds, reason, type) -> {
                    this.written.add(id + " " + seconds + " " + reason + " " + type);
                    return mock(ModToolBan.class);
                },
                (user, ban) -> {},
                id -> {},
                id -> id == 7));
        CommandRegistry registry = new CommandRegistry();
        registry.register("banuser", BanUser.class);
        registry.register("unbanuser", UnbanUser.class);
        return registry;
    }

    @Test
    void bansWithTheGivenReasonAndDuration() {
        CommandResult result = registry()
                .dispatch("banuser", "{\"user_id\":7,\"duration\":86400,\"reason\":\" spam \",\"type\":\"account\"}");

        assertEquals(RCONMessage.STATUS_OK, result.status(), result.toResponseJson());
        assertEquals(List.of("7 86400 spam ACCOUNT"), this.written);
    }

    @Test
    void refusesWhatTheEmulatorWouldNotStore() {
        CommandRegistry registry = registry();
        assertEquals(
                RCONMessage.STATUS_ERROR,
                registry.dispatch("banuser", "{\"user_id\":7,\"duration\":0,\"reason\":\"x\"}")
                        .status());
        assertEquals(
                RCONMessage.STATUS_ERROR,
                registry.dispatch("banuser", "{\"user_id\":7,\"duration\":1576800001,\"reason\":\"x\"}")
                        .status());
        assertEquals(
                RCONMessage.STATUS_ERROR,
                registry.dispatch("banuser", "{\"user_id\":7,\"duration\":60,\"reason\":\" \"}")
                        .status());
        assertEquals(
                RCONMessage.STATUS_ERROR,
                registry.dispatch("banuser", "{\"user_id\":7,\"duration\":60,\"reason\":\"x\",\"type\":\"room\"}")
                        .status());
        assertEquals(
                RCONMessage.USER_NOT_FOUND,
                registry.dispatch("banuser", "{\"user_id\":8,\"duration\":60,\"reason\":\"x\"}")
                        .status());
        assertTrue(this.written.isEmpty());
    }

    @Test
    void unbansAPlayerWithAnActiveBan() {
        CommandRegistry registry = registry();
        assertEquals(
                RCONMessage.STATUS_OK,
                registry.dispatch("unbanuser", "{\"user_id\":7}").status());
        assertEquals(
                RCONMessage.USER_NOT_FOUND,
                registry.dispatch("unbanuser", "{\"user_id\":8}").status());
    }

    @Test
    void waitsForStartUp() {
        BanUser command = new BanUser();
        BanUser.BanUserJSON json = new BanUser.BanUserJSON();
        json.user_id = 7;
        json.duration = 60;
        json.reason = "x";
        command.handle(null, json);
        assertEquals(RCONMessage.STATUS_ERROR, command.status);
    }

    @Test
    void readsTheBanType() {
        assertEquals(ModToolBanType.ACCOUNT, BanUser.type(null));
        assertEquals(ModToolBanType.IP, BanUser.type(" IP "));
        assertEquals(ModToolBanType.SUPER, BanUser.type("super"));
        assertNull(BanUser.type("room"));
    }
}
