package polaris.hotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.permissions.Rank;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

/**
 * An IP or super ban carried over to every account on the same address, the moderator included
 * when they held the highest rank, and :ipban then banned each of those accounts again, repeating
 * the whole walk for every one: behind a proxy an admin's :ipban banned everybody, the admin too,
 * with n squared rows.
 */
class SharedAccountBanTest {

    private final Map<Integer, UserInfo> infos = new HashMap<>();

    private User user(int id, int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        this.infos.put(id, info);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    private List<User> toBan(List<User> sharing, int targetUserId, User moderator) {
        Rank top = mock(Rank.class);
        when(top.getId()).thenReturn(7);
        PermissionsManager permissions = mock(PermissionsManager.class);
        when(permissions.getAllRanks()).thenReturn(List.of(top));
        UserManager users = mock(UserManager.class);
        when(users.getUserInfo(anyInt())).thenAnswer(i -> this.infos.get(i.<Integer>getArgument(0)));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getPermissionsManager()).thenReturn(permissions);
        when(environment.getUserManager()).thenReturn(users);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            return ModToolManager.sharedAccountsToBan(sharing, targetUserId, moderator);
        }
    }

    @Test
    void neitherTheTargetNorTheModeratorIsBannedAgain() {
        User admin = this.user(1, 7);
        User target = this.user(2, 1);
        User alt = this.user(3, 1);

        assertEquals(List.of(alt), this.toBan(List.of(admin, target, alt), 2, admin));
    }

    @Test
    void aRankTheModeratorMayNotModerateIsSkipped() {
        User moderator = this.user(1, 4);
        User admin = this.user(2, 6);
        User alt = this.user(3, 1);

        assertEquals(List.of(alt), this.toBan(List.of(admin, alt), 9, moderator));
    }

    @Test
    void oneBanCarriesOverToABoundedNumberOfAccounts() {
        User admin = this.user(1, 7);
        List<User> crowd = new ArrayList<>();
        for (int id = 10; id < 10 + ModToolManager.MAX_SHARED_ACCOUNT_BANS * 3; id++) {
            crowd.add(this.user(id, 1));
        }

        assertEquals(
                ModToolManager.MAX_SHARED_ACCOUNT_BANS,
                this.toBan(crowd, 2, admin).size());
    }

    @Test
    void ipBanCommandBansOnce() throws Exception {
        String command = Files.readString(Path.of("src/main/java/polaris/hotel/commands/IPBanCommand.java"));
        String manager = Files.readString(Path.of("src/main/java/polaris/hotel/modtool/ModToolManager.java"));

        assertFalse(
                command.contains("getUsersWithIP"), "the command no longer bans each account on the address itself");
        assertTrue(manager.contains("sharedAccountsToBan(sharing, targetUserId, moderator)"));
        assertTrue(
                manager.contains("Set<User> sharing = new LinkedHashSet<>();"),
                "an account on both address and machine is banned once");
    }
}
