package com.eu.habbo.database;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistenceTaskRoutingContractTest {

    @Test
    void sqlOnlyRunnablesDoNotUseTheGameScheduler() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        List<Path> sources;
        try (var paths = Files.walk(sourceRoot)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path source : sources) {
            String code = Files.readString(source);
            assertFalse(code.contains("Emulator.getThreading().run(" + "new QueryDeleteHabboItem"), source.toString());
            assertFalse(code.contains("Emulator.getThreading().run(" + "new SaveScoreForTeam"), source.toString());
            assertFalse(code.contains("Emulator.getThreading().run(" + "new UpdateModToolIssue"), source.toString());
        }
    }
}
