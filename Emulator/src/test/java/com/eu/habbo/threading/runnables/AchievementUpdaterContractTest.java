package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** One user that failed stopped the online-time loop for every user after it. */
class AchievementUpdaterContractTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/threading/runnables/AchievementUpdater.java");

    @Test
    void eachUserIsGuardedAndAMissingAchievementStopsTheRun() throws Exception {
        String source = Files.readString(SOURCE);

        int missing = source.indexOf("if (onlineTime == null) return;");
        int loop = source.indexOf("getOnlineHabbos()");
        int guard = source.indexOf("} catch (RuntimeException e) {", loop);

        assertTrue(missing > 0 && missing < loop, "a hotel without the achievement stops before the loop");
        assertTrue(guard > loop, "a failing user is caught inside the loop");
    }
}
