package com.eu.habbo.messages.outgoing.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.Rank;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserPerksComposerTest {
    private static final UserPerksComposer.Perk ON_BY_DEFAULT =
            new UserPerksComposer.Perk("MOUSE_ZOOM", "", "acc_perk_mouse_zoom", true);
    private static final UserPerksComposer.Perk OFF_BY_DEFAULT =
            new UserPerksComposer.Perk("CAMERA", "", "acc_camera", false);

    @Test
    void aPerkTheRankDoesNotDefineKeepsItsDefault() {
        Rank rank = new Rank(1);

        assertTrue(UserPerksComposer.isAllowed(ON_BY_DEFAULT, rank, false));
        assertFalse(UserPerksComposer.isAllowed(OFF_BY_DEFAULT, rank, false));
    }

    @Test
    void aRankCanSwitchADefaultPerkOff() {
        Rank rank = new Rank(1);
        rank.setPermission("acc_perk_mouse_zoom", PermissionSetting.DISALLOWED);

        assertFalse(UserPerksComposer.isAllowed(ON_BY_DEFAULT, rank, false));
        assertTrue(UserPerksComposer.isAllowed(ON_BY_DEFAULT, rank, true), "a plugin grant still counts");
    }

    @Test
    void everyPerkIsSentOnce() {
        Set<String> codes = new HashSet<>();

        for (UserPerksComposer.Perk perk : UserPerksComposer.PERKS) {
            assertTrue(codes.add(perk.code()), perk.code() + " is sent twice");
        }

        assertEquals(14, codes.size());
    }
}
