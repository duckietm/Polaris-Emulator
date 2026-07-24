package com.eu.habbo.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.packaging.probe.PackagedJarProbe;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackagedJarContractIT {

    @Test
    void executableJarPreservesVersionAndFlywayServiceDiscovery() throws Exception {
        Path jar = packagedJar();
        try (JarFile archive = new JarFile(jar.toFile())) {
            assertEquals(
                    "com.eu.habbo.Emulator",
                    archive.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
        }

        Process process = packagedJarProbe(jar, Path.of("").toAbsolutePath());

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Packaged JAR probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("Packaged JAR contract verified"), output);
    }

    @Test
    void assembledJarMatchesPluginVisibleClasspathAndMergedResourceManifests() throws Exception {
        try (JarFile archive = new JarFile(packagedJar().toFile())) {
            for (String contract : resourceLines("/packaging/plugin-visible-classpath.contract")) {
                boolean expected = contract.startsWith("+ ");
                String entry = contract.substring(2);
                if (expected) {
                    assertTrue(archive.getEntry(entry) != null, "Missing plugin-visible class: " + entry);
                } else {
                    assertFalse(archive.getEntry(entry) != null, "Unexpected plugin-visible class: " + entry);
                }
            }

            String servicePath = "META-INF/services/org.flywaydb.core.extensibility.Plugin";
            try (InputStream input = archive.getInputStream(archive.getJarEntry(servicePath))) {
                Set<String> actual = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toCollection(TreeSet::new));
                assertEquals(new TreeSet<>(resourceLines("/packaging/flyway-plugin-providers.contract")), actual);
            }
        }
    }

    @Test
    void baselinePluginsLoadFromAssembledJarWithLegacyLifecycleAndResources(@TempDir Path temp) throws Exception {
        Path morningstar = Files.createDirectories(temp.resolve("morningstar"));
        runPluginFixture(
                LegacyPluginFixtureCompiler.compileMorningstarPlugin(
                        Files.createDirectories(morningstar.resolve("compile"))),
                morningstar);

        Path polaris = Files.createDirectories(temp.resolve("polaris"));
        runPluginFixture(
                LegacyPluginFixtureCompiler.compileReleasedPolarisPlugin(
                        Files.createDirectories(polaris.resolve("compile"))),
                polaris);

        Path current = Files.createDirectories(temp.resolve("current-wired-snapshot"));
        runPluginFixture(
                LegacyPluginFixtureCompiler.compileCurrentWiredSnapshotPlugin(
                        Files.createDirectories(current.resolve("compile")), packagedJar()),
                current);
    }

    private static void runPluginFixture(Path fixture, Path workDirectory) throws Exception {
        Path plugins = Files.createDirectories(workDirectory.resolve("plugins"));
        Files.copy(fixture, plugins.resolve(fixture.getFileName()));
        Process process = packagedJarProbe(packagedJar(), workDirectory, "plugin");

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Legacy plugin probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("Legacy plugin contract verified"), output);
    }

    private static Process packagedJarProbe(Path jar, Path workingDirectory, String... arguments) throws Exception {
        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java");
        String classpath = String.join(
                File.pathSeparator,
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                jar.toAbsolutePath().toString());
        List<String> command =
                new java.util.ArrayList<>(List.of(java.toString(), "-cp", classpath, PackagedJarProbe.class.getName()));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
    }

    private static List<String> resourceLines(String name) throws Exception {
        try (InputStream input = PackagedJarContractIT.class.getResourceAsStream(name)) {
            assertTrue(input != null, "Missing test resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
    }

    private static Path packagedJar() throws Exception {
        try (var files = Files.list(Path.of("target"))) {
            List<Path> jars = files.filter(
                            path -> path.getFileName().toString().matches("Polaris-.+-jar-with-dependencies\\.jar"))
                    .toList();
            assertEquals(1, jars.size(), "Expected one packaged Polaris executable JAR");
            return jars.getFirst();
        }
    }
}
