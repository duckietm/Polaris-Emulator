package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.modtool.ModToolBanType;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

class BanUserRconTest {
    private static final Gson GSON = new Gson();

    private final GameEnvironment environment = mock(GameEnvironment.class);
    private final ModToolManager modTools = mock(ModToolManager.class);
    private final UserManager users = mock(UserManager.class);

    BanUserRconTest() {
        when(this.environment.getModToolManager()).thenReturn(this.modTools);
        when(this.environment.getUserManager()).thenReturn(this.users);
    }

    private BanUser run(String json) {
        BanUser command = new BanUser(() -> this.environment);
        BanUser.JSON payload = GSON.fromJson(json, BanUser.JSON.class);

        if (command.validate(payload)) {
            command.handle(GSON, payload);
        }

        return command;
    }

    @Test
    void theUserTheDurationAndTheReasonAreChecked() {
        assertEquals("invalid user", this.run("{\"user_id\":0,\"duration\":60}").message);
        assertEquals(
                "duration must be a positive number of seconds", this.run("{\"user_id\":3,\"duration\":0}").message);
        assertEquals(
                "reason must be at most 200 characters",
                this.run("{\"user_id\":3,\"duration\":60,\"reason\":\"" + "x".repeat(201) + "\"}").message);
        verify(this.modTools, never()).banAsSystem(anyInt(), any(), anyInt(), any());
    }

    @Test
    void theTypesAreAccountIpMachineAndSuper() {
        assertEquals(ModToolBanType.ACCOUNT, BanUser.banType(null));
        assertEquals(ModToolBanType.ACCOUNT, BanUser.banType("account"));
        assertEquals(ModToolBanType.IP, BanUser.banType("IP"));
        assertEquals(ModToolBanType.MACHINE, BanUser.banType("machine"));
        assertEquals(ModToolBanType.SUPER, BanUser.banType("super"));
        assertNull(BanUser.banType("forever"));

        BanUser command = this.run("{\"user_id\":3,\"duration\":60,\"type\":\"forever\"}");
        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertEquals("type must be one of: account, ip, machine, super", command.message);
    }

    @Test
    void anUnknownUserIsUserNotFound() {
        BanUser command = this.run("{\"user_id\":3,\"duration\":60}");

        assertEquals(RCONMessage.USER_NOT_FOUND, command.status);
        verify(this.modTools, never()).banAsSystem(anyInt(), any(), anyInt(), any());
    }

    @Test
    void aBanGoesThroughTheSystemPathAndIsAudited() {
        when(this.users.getUserInfo(3)).thenReturn(mock(UserInfo.class));
        ModToolBan ban = mock(ModToolBan.class);
        ban.type = ModToolBanType.IP;
        when(this.modTools.banAsSystem(3, "spam", 3600, ModToolBanType.IP)).thenReturn(List.of(ban));

        try (MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            BanUser command = this.run("{\"user_id\":3,\"duration\":3600,\"reason\":\" spam \",\"type\":\"ip\"}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            assertEquals("", command.message);
            verify(this.modTools).banAsSystem(3, "spam", 3600, ModToolBanType.IP);
            audit.verify(() -> HousekeepingAuditLog.log(
                    eq(0), eq("All-in-1 Manager"), eq("user.ban"), eq(3), anyString(), eq("rcon")));
        }
    }

    @Test
    void aBanThatPlacedNothingIsASystemError() {
        when(this.users.getUserInfo(3)).thenReturn(mock(UserInfo.class));
        when(this.modTools.banAsSystem(anyInt(), any(), anyInt(), any())).thenReturn(List.of());

        BanUser command = this.run("{\"user_id\":3,\"duration\":60}");

        assertEquals(RCONMessage.SYSTEM_ERROR, command.status);
        assertEquals("ban failed", command.message);
    }
}
