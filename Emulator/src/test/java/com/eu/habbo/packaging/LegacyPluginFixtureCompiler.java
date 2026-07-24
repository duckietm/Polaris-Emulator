package com.eu.habbo.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

final class LegacyPluginFixtureCompiler {

    private static final Path FIXTURE_ROOT = Path.of("src", "test", "resources", "plugin-fixtures");

    private LegacyPluginFixtureCompiler() {}

    static Path compileMorningstarPlugin(Path workDirectory) throws Exception {
        Path fixture = FIXTURE_ROOT.resolve("morningstar");
        return compilePlugin(
                workDirectory,
                fixture,
                "LegacyBehaviorPlugin.java",
                "morningstar-compatibility-fixture.jar",
                "8",
                List.of(
                        Path.of("abi-baseline", "arcturus-morningstar-3.5.5-api.jar"),
                        codeSource("gnu.trove.map.TMap")));
    }

    static Path compileReleasedPolarisPlugin(Path workDirectory) throws Exception {
        Path fixture = FIXTURE_ROOT.resolve("polaris");
        return compilePlugin(
                workDirectory,
                fixture,
                "ReleasedBehaviorPlugin.java",
                "released-polaris-compatibility-fixture.jar",
                "25",
                List.of(Path.of("abi-baseline", "polaris-release-api.jar")));
    }

    static Path compileCurrentWiredSnapshotPlugin(Path workDirectory, Path currentJar) throws Exception {
        Path fixture = FIXTURE_ROOT.resolve("current-wired-snapshot");
        return compilePlugin(
                workDirectory,
                fixture,
                "WiredSnapshotProviderPlugin.java",
                "current-wired-snapshot-provider-fixture.jar",
                "25",
                List.of(currentJar));
    }

    private static Path compilePlugin(
            Path workDirectory, Path fixture, String sourceName, String jarName, String release, List<Path> classpath)
            throws Exception {
        Path classes = Files.createDirectories(workDirectory.resolve("classes"));
        Path source = fixture.resolve(sourceName);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Legacy plugin fixtures require a full JDK");

        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(
                null,
                diagnostics,
                diagnostics,
                "--release",
                release,
                "-classpath",
                classpath.stream()
                        .map(Path::toAbsolutePath)
                        .map(Path::toString)
                        .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator)),
                "-d",
                classes.toString(),
                source.toString());
        assertEquals(0, result, diagnostics.toString(java.nio.charset.StandardCharsets.UTF_8));

        Path jar = workDirectory.resolve(jarName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClasses(output, classes);
            addResource(output, fixture.resolve("plugin.json"), "plugin.json");
            addResource(output, fixture.resolve("plugin-resource.fixture"), "fixture/plugin-resource.txt");
        }
        return jar;
    }

    private static Path codeSource(String className) throws Exception {
        Class<?> type = Class.forName(className);
        URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
        return Path.of(location);
    }

    private static void addClasses(JarOutputStream output, Path classes) throws IOException {
        try (Stream<Path> files = Files.walk(classes)) {
            List<Path> classFiles = files.filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .toList();
            for (Path classFile : classFiles) {
                String name = classes.relativize(classFile).toString().replace('\\', '/');
                addResource(output, classFile, name);
            }
        }
    }

    private static void addResource(JarOutputStream output, Path source, String name) throws IOException {
        output.putNextEntry(new JarEntry(name));
        Files.copy(source, output);
        output.closeEntry();
    }
}
