package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeResilienceWiringContractTest {

    @Test
    void bootstrapInstallsResilienceAfterConfigurationDefaultsAreRegistered() throws Exception {
        String source = source("src/main/java/com/eu/habbo/PolarisBootstrap.java");
        int defaults = source.indexOf("registerConfigurationDefaults.run()");
        int install = source.indexOf("RuntimeResilienceRuntime.install(");

        assertTrue(defaults >= 0);
        assertTrue(install > defaults);
    }

    @Test
    void loginAndStressUseTheirDeclaredWorkClasses() throws Exception {
        String login = source("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java");
        String stress = source("src/main/java/com/eu/habbo/messages/rcon/StressStart.java");

        assertTrue(login.contains("RuntimeResilienceController.WorkClass.INTERACTIVE"));
        assertTrue(stress.contains("RuntimeResilienceController.WorkClass.BACKGROUND"));
    }

    @Test
    void loginTransportGuardsRunBeforeRuntimeAdmissionCanSendAResponse() throws Exception {
        String login = source("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java");

        int encryptionGuard = login.indexOf("encryption.forced");
        int releaseGuard = login.indexOf("ClientReleaseGuard.isAllowed");
        int admission = login.indexOf("RuntimeResilienceRuntime.admit(");

        assertTrue(encryptionGuard >= 0);
        assertTrue(releaseGuard > encryptionGuard);
        assertTrue(admission > releaseGuard);
    }

    @Test
    void operationalSnapshotExportsTheSharedResilienceState() throws Exception {
        String stats = source("src/main/java/com/eu/habbo/monitoring/EmulatorStatsService.java");

        assertTrue(stats.contains("RuntimeResilienceRuntime.status()"));
        assertTrue(stats.contains("RuntimeResilienceService.Status resilience"));
    }

    @Test
    void configurationRegistryOwnsAllResilienceSettings() throws Exception {
        String registry = source("src/main/java/com/eu/habbo/core/config/ConfigRegistry.java");

        assertTrue(registry.contains("runtime.resilience.mode"));
        assertTrue(registry.contains("runtime.resilience.degraded.percent"));
        assertTrue(registry.contains("runtime.resilience.critical.percent"));
        assertTrue(registry.contains("runtime.resilience.circuit.failure_percent"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
