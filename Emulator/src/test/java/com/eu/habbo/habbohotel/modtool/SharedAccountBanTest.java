package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * An IP or super ban carried over to every account on the same address, the moderator included
 * when they held the highest rank, and :ipban then banned each of those accounts again, repeating
 * the whole walk for every one: behind a proxy an admin's :ipban banned everybody, the admin too,
 * with n squared rows.
 */
class SharedAccountBanTest {

    private final Map<Integer, HabboInfo> infos = new HashMap<>();

    private Habbo habbo(int id, int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        this.infos.put(id, info);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private List<Habbo> toBan(List<Habbo> sharing, int targetUserId, Habbo moderator) {
        Rank top = mock(Rank.class);
        when(top.getId()).thenReturn(7);
        PermissionsManager permissions = mock(PermissionsManager.class);
        when(permissions.getAllRanks()).thenReturn(List.of(top));
        HabboManager habbos = mock(HabboManager.class);
        when(habbos.getHabboInfo(anyInt())).thenAnswer(i -> this.infos.get(i.<Integer>getArgument(0)));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getPermissionsManager()).thenReturn(permissions);
        when(environment.getHabboManager()).thenReturn(habbos);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            return ModToolManager.sharedAccountsToBan(sharing, targetUserId, moderator);
        }
    }

    @Test
    void neitherTheTargetNorTheModeratorIsBannedAgain() {
        Habbo admin = this.habbo(1, 7);
        Habbo target = this.habbo(2, 1);
        Habbo alt = this.habbo(3, 1);

        assertEquals(List.of(alt), this.toBan(List.of(admin, target, alt), 2, admin));
    }

    @Test
    void aRankTheModeratorMayNotModerateIsSkipped() {
        Habbo moderator = this.habbo(1, 4);
        Habbo admin = this.habbo(2, 6);
        Habbo alt = this.habbo(3, 1);

        assertEquals(List.of(alt), this.toBan(List.of(admin, alt), 9, moderator));
    }

    @Test
    void oneBanCarriesOverToABoundedNumberOfAccounts() {
        Habbo admin = this.habbo(1, 7);
        List<Habbo> crowd = new ArrayList<>();
        for (int id = 10; id < 10 + ModToolManager.MAX_SHARED_ACCOUNT_BANS * 3; id++) {
            crowd.add(this.habbo(id, 1));
        }

        assertEquals(
                ModToolManager.MAX_SHARED_ACCOUNT_BANS,
                this.toBan(crowd, 2, admin).size());
    }

    @Test
    void ipBanCommandBansOnce() throws Exception {
        String command = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/IPBanCommand.java"));
        String manager = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/ModToolManager.java"));

        assertFalse(
                command.contains("getHabbosWithIP"), "the command no longer bans each account on the address itself");
        assertTrue(manager.contains("sharedAccountsToBan(sharing, targetUserId, moderator)"));
        assertTrue(
                manager.contains("Set<Habbo> sharing = new LinkedHashSet<>();"),
                "an account on both address and machine is banned once");
    }
}
