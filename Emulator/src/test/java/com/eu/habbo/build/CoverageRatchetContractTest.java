package com.eu.habbo.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CoverageRatchetContractTest {
    /** What the tests covered when the minimum was last raised; the minimum in the pom can only go up from here. */
    private static final double BASELINE = 0.35;

    private static final Pattern MINIMUM =
            Pattern.compile("<jacoco\\.minimum\\.instruction\\.coveredratio>([0-9.]+)</jacoco\\.minimum");

    private static double minimumCoveredRatio(String pom) {
        Matcher matcher = MINIMUM.matcher(pom);

        assertTrue(matcher.find(), "the pom defines jacoco.minimum.instruction.coveredratio");

        return Double.parseDouble(matcher.group(1));
    }

    @Test
    void coverageProfileUsesAJava25CapableAgentAndFailsBelowTheBaseline() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<jacoco-plugin.version>0.8.15</jacoco-plugin.version>"));
        assertTrue(
                minimumCoveredRatio(pom) >= BASELINE,
                "The coverage minimum must not go back under " + BASELINE + ": raise it as the coverage rises");
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
