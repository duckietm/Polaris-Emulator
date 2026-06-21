package com.eu.habbo.messages.incoming.users;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInputGuardContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/users/" + name + ".java"));
    }

    @Test
    void userProfileLookupsRejectInvalidIdsBeforeOfflineQueries() throws Exception {
        for (String handler : new String[]{"RequestUserProfileEvent", "RequestProfileFriendsEvent", "RequestWearingBadgesEvent"}) {
            String source = source(handler);
            int read = source.indexOf("this.packet.readInt()");
            int guard = source.indexOf("UserInputGuard.isPositiveId", read);
            int lookup = Math.max(source.indexOf("getOfflineHabboInfo", guard), source.indexOf("getBadgesOfflineHabbo", guard));

            assertTrue(guard > read, handler + " should validate the packet id after reading it");
            assertTrue(lookup == -1 || guard < lookup, handler + " should validate ids before offline lookups");
        }
    }

    @Test
    void settingsInputsAreNormalizedBeforePersistence() throws Exception {
        String volumes = source("SaveUserVolumesEvent");
        String flags = source("UpdateUIFlagsEvent");

        assertTrue(volumes.contains("UserInputGuard.clampVolume(this.packet.readInt())"),
                "volume settings should be clamped to the client-supported range");
        assertTrue(flags.contains("UserInputGuard.sanitizeUiFlags(this.packet.readInt())"),
                "UI flags should be sanitized before persistence");
    }

    @Test
    void mottoIsNormalizedAndRejectedBeforeSaveSideEffects() throws Exception {
        String motto = source("SaveMottoEvent");

        int pluginValue = motto.indexOf("UserInputGuard.normalizeText(event.newMotto)");
        int lengthGuard = motto.indexOf("motto.length() > Emulator.getConfig().getInt", pluginValue);
        int save = motto.indexOf("setMotto(motto)", lengthGuard);
        int achievement = motto.indexOf("AchievementManager.progressAchievement", save);

        assertTrue(pluginValue > -1, "plugin-mutated motto should be normalized");
        assertTrue(lengthGuard > pluginValue && lengthGuard < save,
                "motto length should be validated before saving");
        assertTrue(save < achievement,
                "motto achievement should only progress after a valid save");
    }

    @Test
    void helperClampsAndMasksValues() {
        assertEquals(0, UserInputGuard.clampVolume(-20));
        assertEquals(40, UserInputGuard.clampVolume(40));
        assertEquals(100, UserInputGuard.clampVolume(101));
        assertEquals(0, UserInputGuard.sanitizeUiFlags(-1));
        assertEquals(0xFFFF, UserInputGuard.sanitizeUiFlags(0x1FFFF));
    }
}
