package com.eu.habbo.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MavenBuildEnvironmentContractTest {

    @Test
    void pomPinsTheBuildEnvironmentAndDependencyFamilies() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<project.build.outputTimestamp>"));
        assertTrue(pom.contains("<artifactId>maven-enforcer-plugin</artifactId>"));
        assertTrue(pom.contains("<requireJavaVersion>"));
        assertTrue(pom.contains("<version>[25,26)</version>"));
        assertTrue(pom.contains("<requireMavenVersion>"));
        assertTrue(pom.contains("<version>[3.9.11,4)</version>"));
        assertTrue(pom.contains("<dependencyConvergence/>"));
        assertTrue(pom.contains("<requirePluginVersions/>"));
        assertTrue(pom.contains("<artifactId>versions-maven-plugin</artifactId>"));

        assertImportedBom(pom, "io.netty", "netty-bom");
        assertImportedBom(pom, "org.junit", "junit-bom");
        assertImportedBom(pom, "org.testcontainers", "testcontainers-bom");
        assertImportedBom(pom, "org.mockito", "mockito-bom");
        assertFalse(pom.contains("datafaker"));
    }

    @Test
    void wrapperPinsMavenAndVerifiesItsDistribution() throws Exception {
        Path wrapperProperties = Path.of(".mvn", "wrapper", "maven-wrapper.properties");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(wrapperProperties)) {
            properties.load(reader);
        }

        assertTrue(Files.isRegularFile(Path.of("mvnw")));
        assertTrue(Files.isRegularFile(Path.of("mvnw.cmd")));
        assertTrue(properties.getProperty("distributionUrl").contains("apache-maven/3.9.11/"));
        assertTrue(properties.getProperty("distributionSha256Sum").matches("[a-f0-9]{64}"));
    }

    @Test
    void continuousIntegrationUsesThePinnedWrapper() throws Exception {
        String ci = Files.readString(Path.of("..", ".github", "workflows", "ci.yml"));
        String codeQl = Files.readString(Path.of("..", ".github", "workflows", "codeql.yml"));

        assertTrue(ci.contains("run: ./mvnw -B -Pcoverage verify"));
        assertFalse(ci.contains("run: mvn "));
        assertTrue(codeQl.contains("run: ./mvnw -B -DskipTests package"));
    }

    private static void assertImportedBom(String pom, String groupId, String artifactId) {
        assertTrue(pom.contains("<groupId>" + groupId + "</groupId>"));
        assertTrue(pom.contains("<artifactId>" + artifactId + "</artifactId>"));
    }
}
