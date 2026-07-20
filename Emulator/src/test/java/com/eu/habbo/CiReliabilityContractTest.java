package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiReliabilityContractTest {

    @Test
    void githubWorkflowsBoundAndCoordinateRuns() throws Exception {
        String ci = Files.readString(Path.of("../.github/workflows/ci.yml"));
        assertTrue(ci.contains("group: ${{ github.workflow }}-${{ github.ref }}"));
        assertTrue(ci.contains("cancel-in-progress: true"));
        assertTrue(occurrences(ci, "timeout-minutes:") >= 3);

        String release =
                Files.readString(Path.of("../.github/workflows/build-release.yml"));
        assertTrue(
                release.contains(
                        "group: ${{ github.workflow }}-${{ github.ref }}"));
        assertTrue(release.contains("cancel-in-progress: false"));
        assertTrue(release.contains("timeout-minutes:"));
    }

    @Test
    void integrationForksHaveAHardTimeout() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(
                pom.contains(
                        "<forkedProcessTimeoutInSeconds>600"
                                + "</forkedProcessTimeoutInSeconds>"));
    }

    @Test
    void staleGitLabPipelineIsNotAdvertised() {
        assertFalse(Files.exists(Path.of("../.gitlab-ci.yml")));
    }

    private static int occurrences(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
