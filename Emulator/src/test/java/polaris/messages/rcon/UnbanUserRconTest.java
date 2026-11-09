package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

class UnbanUserRconTest {
    private static final Gson GSON = new Gson();

    private final GameEnvironment environment = mock(GameEnvironment.class);
    private final ModToolManager modTools = mock(ModToolManager.class);
    private final UserManager users = mock(UserManager.class);

    UnbanUserRconTest() {
        when(this.environment.getModToolManager()).thenReturn(this.modTools);
        when(this.environment.getUserManager()).thenReturn(this.users);
    }

    private UnbanUser run(String json) {
        UnbanUser command = new UnbanUser(() -> this.environment);
        UnbanUser.JSON payload = GSON.fromJson(json, UnbanUser.JSON.class);

        if (command.validate(payload)) {
            command.handle(GSON, payload);
        }

        return command;
    }

    private void user(int id, String name) {
        UserInfo info = mock(UserInfo.class);
        when(info.getUsername()).thenReturn(name);
        when(this.users.getUserInfo(id)).thenReturn(info);
    }

    @Test
    void theUserIsRequiredAndMustExist() {
        assertEquals("invalid user", this.run("{}").message);

        UnbanUser unknown = this.run("{\"user_id\":8}");

        assertEquals(RCONMessage.USER_NOT_FOUND, unknown.status);
        verify(this.modTools, never()).unban(anyString());
    }

    @Test
    void nothingLiftedIsNoActiveBan() {
        this.user(8, "frank");

        UnbanUser command = this.run("{\"user_id\":8}");

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertEquals("no active ban", command.message);
    }

    @Test
    void everyActiveBanIsLiftedWithoutAModeratorAndAudited() {
        this.user(8, "frank");
        when(this.modTools.unban("frank")).thenReturn(true);

        try (MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            UnbanUser command = this.run("{\"user_id\":8}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            verify(this.modTools).unban("frank");
            audit.verify(() -> HousekeepingAuditLog.log(
                    eq(0), eq("All-in-1 Manager"), eq("user.unban"), eq(8), eq("username=frank"), eq("rcon")));
        }
    }
}
