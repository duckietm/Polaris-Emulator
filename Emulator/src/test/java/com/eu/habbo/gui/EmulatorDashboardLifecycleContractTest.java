package com.eu.habbo.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The dashboard was built off the event dispatch thread and kept collecting snapshots after its window was closed. */
class EmulatorDashboardLifecycleContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/gui/EmulatorDashboard.java"));
    }

    @Test
    void theWindowIsBuiltOnTheEventDispatchThread() throws Exception {
        String dashboard = source();

        int later = dashboard.indexOf("SwingUtilities.invokeLater(() -> {", dashboard.indexOf("public static void launch()"));
        assertTrue(later > 0 && later < dashboard.indexOf("instance = new EmulatorDashboard();"));
    }

    @Test
    void aHiddenWindowCollectsNothing() throws Exception {
        String dashboard = source();

        int update = dashboard.indexOf("private void updateMetrics()");
        int guard = dashboard.indexOf("if (!this.isVisible()) {", update);
        assertTrue(guard > update && guard < dashboard.indexOf("collectSnapshot()", update));
    }
}
