package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A stack its Execution Limit blocked ran its selectors first, and a stack the room budget refused spent an Execution
 * Limit slot without running.
 */
class WiredExecutionLimitOrderTest {

    @Test
    void theLimitIsCheckedBeforeTheSelectorsAndTakenAfterTheRoomBudget() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/wired/core/WiredStackExecutor.java"));
        int peek = source.indexOf("!executionLimitExtra.canExecuteAt(currentTime)");
        int selectors = source.indexOf("this.hooks.executeSelectors(stack, context);");
        int budget = source.indexOf("roomDiagnostics.tryConsumeExecutionBudget(");
        int slot = source.indexOf("!executionLimitExtra.tryAcquireExecutionSlot(currentTime)");
        int effects = source.indexOf("this.hooks.executeEffects(stack, executableEffects, context, currentTime);");

        assertTrue(peek > 0 && selectors > peek);
        assertTrue(budget > selectors && slot > budget && effects > slot);
    }
}
