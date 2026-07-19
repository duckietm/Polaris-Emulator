package com.eu.habbo.packaging;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class LegacyPluginFixtureCompiler {

    private static final Path FIXTURE_ROOT =
            Path.of("src", "test", "resources", "plugin-fixtures", "morningstar");

    private LegacyPluginFixtureCompiler() {
    }

    static Path compileMorningstarPlugin(Path workDirectory) throws Exception {
        Path classes = Files.createDirectories(workDirectory.resolve("classes"));
        Path source = FIXTURE_ROOT.resolve("LegacyBehaviorPlugin.java");
        Path api = Path.of("abi-baseline", "arcturus-morningstar-3.5.5-api.jar");
        Path trove = codeSource("gnu.trove.map.TMap");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Legacy plugin fixtures require a full JDK");

        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(
                null,
                diagnostics,
                diagnostics,
                "--release", "8",
                "-classpath", api.toAbsolutePath() + java.io.File.pathSeparator + trove,
                "-d", classes.toString(),
                source.toString());
        assertEquals(0, result, diagnostics.toString(java.nio.charset.StandardCharsets.UTF_8));

        Path jar = workDirectory.resolve("morningstar-compatibility-fixture.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClasses(output, classes);
            addResource(output, FIXTURE_ROOT.resolve("plugin.json"), "plugin.json");
            addResource(output, FIXTURE_ROOT.resolve("plugin-resource.txt"), "fixture/plugin-resource.txt");
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
            List<Path> classFiles = files
                    .filter(path -> path.toString().endsWith(".class"))
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
