package polaris.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FrozenArchitectureBaselineTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path BASELINE = Path.of("src/test/resources/architecture/" + "frozen-violations.tsv");
    private static final String UPDATE_PROPERTY = "polaris.architecture.update-baseline";

    @Test
    void globalAndPackageViolationsCannotIncrease() throws Exception {
        Snapshot current = scanSources();
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            writeBaseline(current);
            return;
        }

        Snapshot baseline = readBaseline();
        List<String> violations = new ArrayList<>();
        current.emulatorUsage().forEach((usage, count) -> {
            int allowed = baseline.emulatorUsage().getOrDefault(usage, 0);
            if (count > allowed) {
                violations.add(usage + " increased from " + allowed + " to " + count);
            }
        });
        current.mutableStatics().stream()
                .filter(field -> !baseline.mutableStatics().contains(field))
                .map(field -> "new mutable static field: " + field)
                .forEach(violations::add);
        current.outsideOwnedPackages().stream()
                .filter(path -> !baseline.outsideOwnedPackages().contains(path))
                .map(path -> "new source outside owned packages: " + path)
                .forEach(violations::add);
        current.incomingConnectionAcquisitions().forEach((source, count) -> {
            int allowed = baseline.incomingConnectionAcquisitions().getOrDefault(source, 0);
            if (count > allowed) {
                violations.add("incoming handler connection acquisitions in " + source + " increased from " + allowed
                        + " to " + count);
            }
        });

        assertTrue(
                violations.isEmpty(),
                () -> "Frozen architecture baseline violations:\n" + String.join("\n", violations));
    }

    private static Snapshot scanSources() throws IOException {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        Map<String, Integer> emulatorUsage = new TreeMap<>();
        Set<String> mutableStatics = new TreeSet<>();
        Set<String> outsideOwnedPackages = new TreeSet<>();
        Map<String, Integer> incomingConnectionAcquisitions = new TreeMap<>();

        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                CompilationUnit unit = StaticJavaParser.parse(source);
                String relative = normalize(SOURCE_ROOT.relativize(source));
                recordEmulatorUsage(relative, unit, emulatorUsage);
                recordMutableStatics(unit, mutableStatics);
                recordOutsideOwnedPackages(relative, unit, outsideOwnedPackages);
                recordIncomingConnectionAcquisitions(relative, unit, incomingConnectionAcquisitions);
            }
        }

        return new Snapshot(emulatorUsage, mutableStatics, outsideOwnedPackages, incomingConnectionAcquisitions);
    }

    private static void recordEmulatorUsage(String source, CompilationUnit unit, Map<String, Integer> usage) {
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            call.getScope()
                    .filter(FrozenArchitectureBaselineTest::isEmulatorScope)
                    .ifPresent(ignored -> increment(usage, source + "|CALL|" + call.getNameAsString()));
        }
        for (FieldAccessExpr access : unit.findAll(FieldAccessExpr.class)) {
            if (isEmulatorScope(access.getScope())) {
                increment(usage, source + "|FIELD|" + access.getNameAsString());
            }
        }
        for (MethodReferenceExpr reference : unit.findAll(MethodReferenceExpr.class)) {
            if (isEmulatorScope(reference.getScope())) {
                increment(usage, source + "|REFERENCE|" + reference.getIdentifier());
            }
        }
        for (ImportDeclaration declaration : unit.getImports()) {
            if (declaration.isStatic() && declaration.getNameAsString().startsWith("polaris.Emulator")) {
                increment(usage, source + "|STATIC_IMPORT|" + declaration.getNameAsString());
            }
        }
    }

    private static boolean isEmulatorScope(Expression expression) {
        String scope = expression.toString();
        return scope.equals("Emulator") || scope.equals("polaris.Emulator");
    }

    private static void recordMutableStatics(CompilationUnit unit, Set<String> fields) {
        for (FieldDeclaration declaration : unit.findAll(FieldDeclaration.class)) {
            if (!declaration.isStatic() || declaration.isFinal()) {
                continue;
            }

            TypeDeclaration<?> owner = declaringType(declaration);
            String ownerName = owner.getFullyQualifiedName()
                    .orElseGet(() -> unit.getPackageDeclaration()
                            .map(packageDeclaration ->
                                    packageDeclaration.getNameAsString() + "." + owner.getNameAsString())
                            .orElse(owner.getNameAsString()));
            declaration.getVariables().forEach(variable -> fields.add(ownerName + "#" + variable.getNameAsString()));
        }
    }

    private static TypeDeclaration<?> declaringType(FieldDeclaration declaration) {
        Node current = declaration;
        while (current.getParentNode().isPresent()) {
            current = current.getParentNode().orElseThrow();
            if (current instanceof TypeDeclaration<?> type) {
                return type;
            }
        }
        throw new IllegalStateException("Static field has no declaring type: " + declaration);
    }

    private static void recordOutsideOwnedPackages(String source, CompilationUnit unit, Set<String> paths) {
        String packageName = unit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString())
                .orElse("");
        if (!packageName.equals("polaris")
                && !packageName.startsWith("polaris.")
                && !packageName.equals("db.migration")) {
            paths.add(source);
        }
    }

    private static void recordIncomingConnectionAcquisitions(
            String source, CompilationUnit unit, Map<String, Integer> acquisitions) {
        if (!source.startsWith("polaris/messages/incoming/")) {
            return;
        }
        long count = unit.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("getConnection"))
                .count();
        if (count > 0) {
            acquisitions.put(source, Math.toIntExact(count));
        }
    }

    private static Snapshot readBaseline() throws IOException {
        assertTrue(
                Files.isRegularFile(BASELINE),
                () -> "Missing architecture baseline " + BASELINE + "; regenerate with -D" + UPDATE_PROPERTY + "=true");
        Map<String, Integer> usage = new HashMap<>();
        Set<String> mutableStatics = new HashSet<>();
        Set<String> outsidePackages = new HashSet<>();
        Map<String, Integer> incomingConnectionAcquisitions = new HashMap<>();

        for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            switch (parts[0]) {
                case "U" -> usage.put(String.join("|", parts[1], parts[2], parts[3]), Integer.parseInt(parts[4]));
                case "M" -> mutableStatics.add(parts[1]);
                case "P" -> outsidePackages.add(parts[1]);
                case "D" -> incomingConnectionAcquisitions.put(parts[1], Integer.parseInt(parts[2]));
                default -> throw new IllegalStateException("Unknown architecture baseline entry: " + line);
            }
        }
        return new Snapshot(usage, mutableStatics, outsidePackages, incomingConnectionAcquisitions);
    }

    private static void writeBaseline(Snapshot snapshot) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Frozen Polaris architecture violations.");
        lines.add("# U=direct Emulator usage, M=mutable static, "
                + "P=source outside owned packages, D=incoming handler JDBC acquisition.");
        snapshot.emulatorUsage().forEach((usage, count) -> lines.add("U|" + usage + "|" + count));
        snapshot.mutableStatics().forEach(field -> lines.add("M|" + field));
        snapshot.outsideOwnedPackages().forEach(path -> lines.add("P|" + path));
        snapshot.incomingConnectionAcquisitions().forEach((path, count) -> lines.add("D|" + path + "|" + count));
        Files.createDirectories(BASELINE.getParent());
        Files.write(BASELINE, lines, StandardCharsets.UTF_8);
    }

    private static void increment(Map<String, Integer> values, String key) {
        values.merge(key, 1, Integer::sum);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record Snapshot(
            Map<String, Integer> emulatorUsage,
            Set<String> mutableStatics,
            Set<String> outsideOwnedPackages,
            Map<String, Integer> incomingConnectionAcquisitions) {}
}
