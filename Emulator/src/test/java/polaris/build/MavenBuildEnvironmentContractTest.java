package polaris.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MavenBuildEnvironmentContractTest {

    @Test
    void pomPinsTheBuildEnvironmentAndDependencyFamilies() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<project.build.outputTimestamp>"));
        assertTrue(pom.contains("<artifactId>maven-enforcer-plugin</artifactId>"));
        assertTrue(pom.contains("<requireJavaVersion>"));
        assertTrue(pom.contains("<maven.build.java.version>[25,27)</maven.build.java.version>"));
        assertTrue(pom.contains("<version>${maven.build.java.version}</version>"));
        assertTrue(pom.contains("<requireMavenVersion>"));
        assertTrue(pom.contains("<version>[3.9.11,4)</version>"));
        assertTrue(pom.contains("<dependencyConvergence/>"));
        assertTrue(pom.contains("<requirePluginVersions/>"));
        assertTrue(pom.contains("<artifactId>versions-maven-plugin</artifactId>"));
        assertTrue(pom.contains("<artifactId>maven-dependency-plugin</artifactId>"));

        assertImportedBom(pom, "io.netty", "netty-bom");
        assertImportedBom(pom, "org.junit", "junit-bom");
        assertImportedBom(pom, "org.testcontainers", "testcontainers-bom");
        assertImportedBom(pom, "org.mockito", "mockito-bom");
        assertTrue(pom.contains("<artifactId>netty-common</artifactId>"));
        assertTrue(pom.contains("<artifactId>netty-buffer</artifactId>"));
        assertTrue(pom.contains("<artifactId>netty-transport</artifactId>"));
        assertTrue(pom.contains("<artifactId>netty-codec-base</artifactId>"));
        assertTrue(pom.contains("<artifactId>netty-codec-http</artifactId>"));
        assertTrue(pom.contains("<artifactId>netty-handler</artifactId>"));
        assertFalse(pom.contains("<artifactId>netty-all</artifactId>"));
        assertTrue(pom.contains("<artifactId>testcontainers-mariadb</artifactId>"));
        assertTrue(pom.contains("<artifactId>testcontainers-junit-jupiter</artifactId>"));
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
        assertTrue(wrapperMavenVersionSatisfiesTheEnforcerRange(properties.getProperty("distributionUrl")));
        assertTrue(properties.getProperty("distributionSha256Sum").matches("[a-f0-9]{64}"));
    }

    @Test
    void continuousIntegrationUsesThePinnedWrapper() throws Exception {
        String ci = Files.readString(Path.of("..", ".github", "workflows", "ci.yml"));
        String codeQl = Files.readString(Path.of("..", ".github", "workflows", "codeql.yml"));

        assertTrue(ci.contains("run: ./mvnw -B -Pcoverage verify"));
        assertTrue(ci.contains("name: JDK 26 package compatibility"));
        assertTrue(ci.contains("java-version: \"26\""));
        assertTrue(ci.contains("run: ./mvnw -B clean package"));
        assertFalse(ci.contains("run: mvn "));
        assertTrue(codeQl.contains("run: ./mvnw -B -DskipTests package"));
    }

    // The wrapper is bumped by dependency updates: any 3.x release from 3.9.11 on is what the
    // enforcer rule in the pom accepts, so the test checks that instead of one exact version.
    private static boolean wrapperMavenVersionSatisfiesTheEnforcerRange(String distributionUrl) {
        Matcher matcher =
                Pattern.compile("apache-maven/(\\d+)\\.(\\d+)\\.(\\d+)/").matcher(distributionUrl);

        if (!matcher.find()) {
            return false;
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));

        return major == 3 && (minor > 9 || (minor == 9 && patch >= 11));
    }

    private static void assertImportedBom(String pom, String groupId, String artifactId) {
        assertTrue(pom.contains("<groupId>" + groupId + "</groupId>"));
        assertTrue(pom.contains("<artifactId>" + artifactId + "</artifactId>"));
    }
}
