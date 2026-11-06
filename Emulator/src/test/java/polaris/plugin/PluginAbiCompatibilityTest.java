package polaris.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibilityChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import polaris.plugin.remap.LegacyNames;
import polaris.plugin.remap.LegacyRemapper;

/**
 * Plugin ABI gates. Plugins in production hotels are loaded via URLClassLoader
 * (see {@link PluginManager}) and link against the emulator's entire
 * public/protected surface at the bytecode level. That surface is a frozen ABI
 * with two baselines:
 *
 * <ul>
 *   <li><b>Arcturus Morningstar 3.5.5</b> — the ancestor release most
 *       third-party plugin jars were compiled against.</li>
 *   <li><b>The latest released Polaris jar</b> — plugins compiled against
 *       Polaris itself may use API added since 3.5.5, so every shipped Polaris
 *       surface is equally frozen.</li>
 * </ul>
 *
 * Divergence that already shipped is pinned per baseline in its
 * accepted-divergence file; any binary incompatibility NOT pinned there fails.
 * See abi-baseline/README.md for the policy, the ABI-safe refactoring playbook,
 * and the release-time baseline update procedure. Re-pinning requires explicit
 * maintainer approval (the committed diff review is the approval):
 *
 * <pre>mvn test -Dtest=PluginAbiCompatibilityTest -Dpolaris.abi.regenerate=true</pre>
 */
class PluginAbiCompatibilityTest {

    private record Gate(
            String name,
            Path baselineJar,
            Path acceptedFile,
            String reviewedAcceptedFileSha256,
            List<String> acceptedFileHeader) {}

    private static final Gate MORNINGSTAR_GATE = new Gate(
            "Arcturus Morningstar 3.5.5",
            Path.of("abi-baseline", "arcturus-morningstar-3.5.5-api.jar"),
            Path.of("abi-baseline", "accepted-divergence-morningstar.txt"),
            "8d5f655074a1a90bbfbb3260920e329eebce0cb17c86135d6b1dde94c3879795",
            List.of(
                    "# Binary-incompatible divergence from the Arcturus Morningstar 3.5.5 plugin ABI",
                    "# that has already shipped in Polaris and is accepted. One token per line.",
                    "# Any incompatibility NOT listed here fails PluginAbiCompatibilityTest."));

    private static final Gate POLARIS_GATE = new Gate(
            "the released Polaris jar (see abi-baseline/README.md for the pinned version)",
            Path.of("abi-baseline", "polaris-release-api.jar"),
            Path.of("abi-baseline", "accepted-divergence-polaris.txt"),
            "da4111fd732beb33104cc62940f6649e9f5ca907ce2bff026c64878412072b29",
            List.of(
                    "# Binary-incompatible divergence from the latest RELEASED Polaris jar that is",
                    "# accepted on this branch. One token per line; kept near-empty by policy —",
                    "# only internal machinery never plausibly touched by plugins may appear here.",
                    "# Any incompatibility NOT listed here fails PluginAbiCompatibilityTest."));

    private static final Path CLASSES_DIR = Path.of("target", "classes");
    private static final String REGENERATE_PROPERTY = "polaris.abi.regenerate";

    private Path currentJar;

    @BeforeEach
    void jarUpCompiledClasses() throws IOException {
        assertTrue(Files.isDirectory(CLASSES_DIR), "Compiled classes not found (run via Maven): " + CLASSES_DIR);
        currentJar = buildJar(
                CLASSES_DIR,
                Files.createDirectories(Path.of("target", "abi-check")).resolve("current-classes.jar"));
    }

    @Test
    void surfaceStaysBinaryCompatibleWithMorningstar355() throws IOException {
        runGate(MORNINGSTAR_GATE);
    }

    @Test
    void surfaceStaysBinaryCompatibleWithReleasedPolaris() throws IOException {
        runGate(POLARIS_GATE);
    }

    private void runGate(Gate gate) throws IOException {
        assertTrue(Files.isRegularFile(gate.baselineJar()), "Missing ABI baseline jar: " + gate.baselineJar());

        // The baselines were compiled against the old names; old plugin jars are rewritten with
        // LegacyNames as they load, so the baseline is rewritten the same way before comparing.
        Path baseline = remapBaseline(gate.baselineJar());
        SortedSet<String> currentTokens = incompatibilityTokens(compare(baseline, currentJar));

        if (System.getProperty(REGENERATE_PROPERTY) != null) {
            writeAcceptedDivergence(gate, currentTokens);
            return;
        }

        assertTrue(
                Files.isRegularFile(gate.acceptedFile()),
                "Missing " + gate.acceptedFile() + "; regenerate with -D" + REGENERATE_PROPERTY + "=true");
        assertEquals(gate.reviewedAcceptedFileSha256(), sha256(gate.acceptedFile()), """
                        The accepted ABI divergence list changed without updating its reviewed digest.
                        Review every added token semantically; do not accept a signature break merely
                        to make japicmp green. Update the digest in PluginAbiCompatibilityTest only
                        in the same explicitly reviewed change.
                        """);

        Set<String> accepted;
        try (Stream<String> lines = Files.lines(gate.acceptedFile())) {
            accepted = lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(PluginAbiCompatibilityTest::currentNames)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        List<String> resolved = accepted.stream()
                .filter(token -> !currentTokens.contains(token))
                .toList();
        if (!resolved.isEmpty()) {
            System.out.println("ABI gate: " + resolved.size() + " accepted divergence token(s) no longer occur;"
                    + " consider pruning " + gate.acceptedFile() + ":");
            resolved.forEach(token -> System.out.println("  " + token));
        }

        List<String> newBreaks = currentTokens.stream()
                .filter(token -> !accepted.contains(token))
                .toList();
        if (!newBreaks.isEmpty()) {
            fail("""
                    %d NEW binary-incompatible change(s) vs %s:

                    %s

                    These would break existing plugin jars in production hotels at runtime
                    (NoSuchMethodError/NoSuchFieldError). Restore the old signatures and delegate
                    to new code instead of changing them — see abi-baseline/README.md. Do not
                    regenerate the accepted-divergence files without explicit maintainer approval.
                    """.formatted(
                            newBreaks.size(),
                            gate.name(),
                            newBreaks.stream().map(token -> "  " + token).collect(Collectors.joining("\n"))));
        }
    }

    /**
     * One stable line per binary-incompatible difference, e.g.
     * {@code method polaris.Emulator#getConfig() METHOD_REMOVED}.
     */
    private static SortedSet<String> incompatibilityTokens(List<JApiClass> classes) {
        SortedSet<String> tokens = new TreeSet<>();
        for (JApiClass clazz : classes) {
            String fqn = clazz.getFullyQualifiedName();
            addTokens(tokens, "class", fqn, clazz.getCompatibilityChanges());
            if (clazz.getSuperclass() != null) {
                addTokens(tokens, "superclass", fqn, clazz.getSuperclass().getCompatibilityChanges());
            }
            clazz.getInterfaces()
                    .forEach(iface -> addTokens(
                            tokens,
                            "interface",
                            fqn + "#" + iface.getFullyQualifiedName(),
                            iface.getCompatibilityChanges()));
            clazz.getConstructors()
                    .forEach(ctor -> addTokens(
                            tokens,
                            "constructor",
                            fqn + "#<init>(" + parameterList(ctor.getParameters()) + ")",
                            ctor.getCompatibilityChanges()));
            clazz.getMethods()
                    .forEach(method -> addTokens(
                            tokens,
                            "method",
                            fqn + "#" + method.getName() + "(" + parameterList(method.getParameters()) + ")",
                            method.getCompatibilityChanges()));
            clazz.getFields()
                    .forEach(field ->
                            addTokens(tokens, "field", fqn + "#" + field.getName(), field.getCompatibilityChanges()));
        }
        return tokens;
    }

    private static void addTokens(
            Set<String> tokens, String kind, String subject, List<JApiCompatibilityChange> changes) {
        for (JApiCompatibilityChange change : changes) {
            if (!change.isBinaryCompatible()) {
                tokens.add(kind + " " + subject + " " + change.getType().name());
            }
        }
    }

    private static String parameterList(List<japicmp.model.JApiParameter> parameters) {
        return parameters.stream().map(japicmp.model.JApiParameter::getType).collect(Collectors.joining(","));
    }

    private static List<JApiClass> compare(Path oldJar, Path newJar) {
        JarArchiveComparatorOptions options = new JarArchiveComparatorOptions();
        options.getIgnoreMissingClasses().setIgnoreAllMissingClasses(true);
        JarArchiveComparator comparator = new JarArchiveComparator(options);
        return comparator.compare(
                new JApiCmpArchive(oldJar.toFile(), "baseline"), new JApiCmpArchive(newJar.toFile(), "current"));
    }

    /** The baseline jar with every class rewritten by {@link LegacyRemapper}, as an old plugin would be. */
    private static Path remapBaseline(Path baselineJar) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile jar = new JarFile(baselineJar.toFile())) {
            for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                if (entry.getName().endsWith(".class")) {
                    try (var input = jar.getInputStream(entry)) {
                        classes.put(entry.getName().substring(0, entry.getName().length() - 6), input.readAllBytes());
                    }
                }
            }
        }
        LegacyRemapper remapper = new LegacyRemapper(LegacyNames.standard(), classes::get);
        String name = baselineJar.getFileName().toString().replace(".jar", "-current-names.jar");
        Path remapped = Files.createDirectories(Path.of("target", "abi-check")).resolve(name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(remapped))) {
            for (byte[] original : new TreeMap<>(classes).values()) {
                byte[] rewritten = remapper.remap(original);
                out.putNextEntry(new JarEntry(new ClassReader(rewritten).getClassName() + ".class"));
                out.write(rewritten);
                out.closeEntry();
            }
        }
        return remapped;
    }

    private static final Pattern QUALIFIED_NAME = Pattern.compile("\\bcom\\.eu\\.habbo\\.[\\w.$]+");
    private static final Pattern MEMBER = Pattern.compile("^(\\w+) ([\\w.$]+)#([\\w$<>]+)");

    /** An accepted-divergence token written with the old names, in the names the gate now reports. */
    private static String currentNames(String token) {
        LegacyNames names = LegacyNames.standard();
        String renamed = QUALIFIED_NAME
                .matcher(token)
                .replaceAll(match -> Matcher.quoteReplacement(
                        names.mapType(match.group().replace('.', '/')).replace('/', '.')));
        Matcher member = MEMBER.matcher(renamed);
        if (!member.find()) {
            return renamed;
        }
        String owner = member.group(2);
        String name = member.group(3);
        String current =
                switch (member.group(1)) {
                    case "method" -> Objects.requireNonNullElse(names.renamedMethod(name), name);
                    case "field" -> names.mapField(owner.replace('.', '/'), name);
                    default -> name;
                };
        return renamed.substring(0, member.start(3)) + current + renamed.substring(member.end(3));
    }

    /** japicmp reads jars, not class directories, so package the compiled classes into a jar. */
    private static Path buildJar(Path classesDir, Path jar) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
                Stream<Path> files = Files.walk(classesDir)) {
            List<Path> classFiles = files.filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .toList();
            for (Path classFile : classFiles) {
                out.putNextEntry(
                        new JarEntry(classesDir.relativize(classFile).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(classFile));
                out.closeEntry();
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return jar;
    }

    private static void writeAcceptedDivergence(Gate gate, SortedSet<String> tokens) throws IOException {
        List<String> lines = new ArrayList<>(gate.acceptedFileHeader());
        lines.add("# Regenerate: mvn test -Dtest=PluginAbiCompatibilityTest -D" + REGENERATE_PROPERTY + "=true");
        lines.add("");
        lines.addAll(tokens);
        Files.write(gate.acceptedFile(), lines);
        System.out.println(
                "ABI gate: wrote " + tokens.size() + " accepted divergence tokens to " + gate.acceptedFile());
    }

    private static String sha256(Path path) throws IOException {
        try {
            // Digest the logical text with line endings normalised: a Windows
            // checkout (core.autocrlf) or editor may materialise this file
            // with CRLF without changing the reviewed content.
            byte[] normalized = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK is missing SHA-256", impossible);
        }
    }
}
