package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java25BuildContractTest {
    @Test
    void mavenAndGithubWorkflowsUseJava25Consistently() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String ci = Files.readString(Path.of("../.github/workflows/ci.yml"));
        String release = Files.readString(Path.of("../.github/workflows/build-release.yml"));

        assertTrue(pom.contains("<maven.compiler.release>25</maven.compiler.release>"));
        assertTrue(ci.contains("Set up JDK 25"));
        assertTrue(ci.contains("java-version: \"25\""));
        assertTrue(release.contains("Set up JDK 25"));
        assertTrue(release.contains("java-version: '25'"));
        assertFalse(ci.contains("JDK 21"));
        assertFalse(release.contains("JDK 21"));
    }
}
