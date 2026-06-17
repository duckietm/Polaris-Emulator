package com.eu.habbo.habbohotel.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTargetGuardContractTest {
    @Test
    void highRiskUserCommandsUseCentralTargetGuard() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/habbohotel/commands");

        for (String command : List.of(
                "AlertCommand.java",
                "BanCommand.java",
                "DisconnectCommand.java",
                "GivePrefixCommand.java",
                "GiveRankCommand.java",
                "IPBanCommand.java",
                "MachineBanCommand.java",
                "MuteCommand.java",
                "RemovePrefixCommand.java",
                "SuperbanCommand.java",
                "UnmuteCommand.java"
        )) {
            String source = Files.readString(base.resolve(command));

            assertTrue(source.contains("CommandTargetGuard.canTarget"),
                    command + " must use the central command target guard for staff/core rank handling");
        }
    }

    @Test
    void rankGrantingUsesCentralAssignmentGuard() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/GiveRankCommand.java"));

        assertTrue(source.contains("CommandTargetGuard.canAssignRank"),
                "GiveRankCommand must guard the assigned rank with the same core-rank semantics");
    }

    @Test
    void targetGuardKeepsCorePeerOverrideCentralized() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/CommandTargetGuard.java"));
        String rule = "targetRankId < moderatorRankId || isCoreRank(moderatorRankId) && targetRankId <= moderatorRankId";

        assertTrue(countOccurrences(source, rule) >= 2,
                "non-core command users must only target lower ranks while the highest/core rank may target peer ranks");
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;

        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }

        return count;
    }
}
