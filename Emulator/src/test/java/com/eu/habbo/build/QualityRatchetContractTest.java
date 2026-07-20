package com.eu.habbo.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityRatchetContractTest {

    @Test
    void pomRatchetsFormattingAndSpotBugsFindings() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>spotless-maven-plugin</artifactId>"));
        assertTrue(pom.contains("<ratchetFrom>${spotless.ratchetFrom}</ratchetFrom>"));
        assertTrue(pom.contains("<palantirJavaFormat>"));
        assertTrue(pom.contains("<forbidWildcardImports/>"));
        assertTrue(pom.contains("<artifactId>spotbugs-maven-plugin</artifactId>"));
        assertTrue(pom.contains("<excludeFilterFile>"));
        assertTrue(pom.contains("<goal>check</goal>"));
    }

    @Test
    void ciChecksFormattingFromTheExactChangeBase() throws Exception {
        String workflow = Files.readString(Path.of("..", ".github", "workflows", "ci.yml"));

        assertTrue(workflow.contains("fetch-depth: 0"));
        assertTrue(workflow.contains("-Dspotless.ratchetFrom="));
        assertTrue(workflow.contains("github.event.pull_request.base.sha"));
        assertTrue(workflow.contains("github.event.before"));
    }

    @Test
    void codeQlBuildsAndAnalyzesJavaExplicitly() throws Exception {
        String workflow = Files.readString(Path.of("..", ".github", "workflows", "codeql.yml"));

        assertTrue(workflow.contains("github/codeql-action/init@v4"));
        assertTrue(workflow.contains("languages: java-kotlin"));
        assertTrue(workflow.contains("build-mode: manual"));
        assertTrue(workflow.contains("mvn -B -DskipTests package"));
        assertTrue(workflow.contains("github/codeql-action/analyze@v4"));
    }
}
