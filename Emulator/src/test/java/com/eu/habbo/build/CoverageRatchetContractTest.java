package com.eu.habbo.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoverageRatchetContractTest {

    @Test
    void coverageProfileUsesAJava25CapableAgentAndFailsBelowTheBaseline() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<jacoco-plugin.version>0.8.15</jacoco-plugin.version>"));
        assertTrue(pom.contains("<id>check-coverage-ratchet</id>"));
        assertTrue(pom.contains("<goal>check</goal>"));
        assertTrue(pom.contains("<counter>INSTRUCTION</counter>"));
        assertTrue(pom.contains("<value>COVEREDRATIO</value>"));
        assertTrue(pom.contains("<minimum>${jacoco.minimum.instruction.coveredratio}</minimum>"));
    }

    @Test
    void continuousIntegrationEnforcesCoverage() throws Exception {
        String workflow = Files.readString(Path.of("..", ".github", "workflows", "ci.yml"));

        assertTrue(workflow.contains("./mvnw -B -Pcoverage verify"));
    }
}
