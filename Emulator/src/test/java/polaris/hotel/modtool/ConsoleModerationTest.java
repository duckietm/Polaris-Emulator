package polaris.hotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/** Bans from the hotel's console (RCON banuser / unbanuser). */
class ConsoleModerationTest {

    private final UserInfo info = mock(UserInfo.class);
    private final User online = mock(User.class);
    private final List<String> calls = new ArrayList<>();
    private ModToolBan stored = mock(ModToolBan.class);
    private boolean isOnline;

    private ConsoleModeration moderation() {
        return new ConsoleModeration(
                id -> id == 7 ? this.info : null,
                id -> this.isOnline && id == 7 ? this.online : null,
                (id, seconds, reason, type) -> {
                    this.calls.add("write " + id + " " + seconds + " " + reason + " " + type);
                    return this.stored;
                },
                (user, ban) -> this.calls.add("removed"),
                id -> this.calls.add("evicted " + id),
                id -> id == 7);
    }

    @Test
    void storesTheBanWithoutAStaffMemberAndDropsAParkedSession() {
        when(this.info.getIpLogin()).thenReturn("10.0.0.1");
        when(this.info.getMachineID()).thenReturn("m-1");

        assertEquals(ConsoleModeration.BanOutcome.BANNED, moderation().ban(7, 3600, "spam", ModToolBanType.MACHINE));
        assertEquals(List.of("write 7 3600 spam MACHINE", "evicted 7"), this.calls);
    }

    @Test
    void disconnectsAnOnlinePlayer() {
        this.isOnline = true;
        moderation().ban(7, 60, "spam", ModToolBanType.ACCOUNT);
        assertTrue(this.calls.contains("removed"), this.calls.toString());
    }

    @Test
    void refusesAnUnknownPlayerAndReportsAFailedWrite() {
        assertEquals(
                ConsoleModeration.BanOutcome.USER_NOT_FOUND, moderation().ban(8, 60, "spam", ModToolBanType.ACCOUNT));
        assertEquals(
                ConsoleModeration.BanOutcome.USER_NOT_FOUND, moderation().ban(0, 60, "spam", ModToolBanType.ACCOUNT));

        this.stored = null;
        assertEquals(ConsoleModeration.BanOutcome.FAILED, moderation().ban(7, 60, "spam", ModToolBanType.ACCOUNT));
        assertFalse(this.calls.contains("evicted 7"));
    }

    @Test
    void narrowsATypeWithoutTheAddressOrMachineItNeeds() {
        assertEquals(ModToolBanType.IP, ConsoleModeration.storedType(ModToolBanType.MACHINE, "10.0.0.1", ""));
        assertEquals(ModToolBanType.ACCOUNT, ConsoleModeration.storedType(ModToolBanType.MACHINE, "", null));
        assertEquals(ModToolBanType.ACCOUNT, ConsoleModeration.storedType(ModToolBanType.IP, " ", "m-1"));
        assertEquals(ModToolBanType.SUPER, ConsoleModeration.storedType(ModToolBanType.SUPER, "10.0.0.1", "m-1"));
        assertEquals(ModToolBanType.ACCOUNT, ConsoleModeration.storedType(null, "10.0.0.1", "m-1"));
    }

    @Test
    void unbansOnlyAKnownPlayer() {
        assertTrue(moderation().unban(7));
        assertFalse(moderation().unban(8));
        assertFalse(moderation().unban(-1));
    }
}
