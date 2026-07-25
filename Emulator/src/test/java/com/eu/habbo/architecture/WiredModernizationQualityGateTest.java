package com.eu.habbo.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Static WIRED ratchets for failure visibility, worker safety and bounded scheduling ownership. */
class WiredModernizationQualityGateTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final List<String> WIRED_PREFIXES = List.of(
            "com/eu/habbo/habbohotel/wired/",
            "com/eu/habbo/habbohotel/items/interactions/wired/",
            "com/eu/habbo/messages/incoming/wired/",
            "com/eu/habbo/messages/outgoing/wired/");
    private static final Set<String> WIRED_ROOM_SOURCES = Set.of(
            "com/eu/habbo/habbohotel/rooms/RoomWiredRuntime.java",
            "com/eu/habbo/habbohotel/rooms/WiredGravityPlanner.java",
            "com/eu/habbo/habbohotel/rooms/WiredGravityService.java",
            "com/eu/habbo/habbohotel/rooms/WiredOpacityService.java",
            "com/eu/habbo/habbohotel/rooms/WiredOpacityState.java");

    @Test
    void wiredCatchBlocksCannotSilentlyDiscardFailures() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Source source : wiredSources()) {
            for (CatchClause catchClause : source.unit().findAll(CatchClause.class)) {
                if (catchClause.getBody().getStatements().isEmpty()) {
                    violations.add(source.path() + ":"
                            + catchClause.getBegin().map(p -> p.line).orElse(0));
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "WIRED catch blocks must preserve behavior but emit bounded, redacted diagnostics:\n"
                        + String.join("\n", violations));
    }

    @Test
    void wiredTickWorkersCannotAcquireDatabaseConnections() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Source source : wiredSources()) {
            if (!source.path().startsWith("com/eu/habbo/habbohotel/wired/tick/")) {
                continue;
            }

            for (ImportDeclaration declaration : source.unit().getImports()) {
                String imported = declaration.getNameAsString();
                if (imported.startsWith("java.sql.") || imported.startsWith("javax.sql.")) {
                    violations.add(source.path() + " imports " + imported);
                }
            }
            for (MethodCallExpr call : source.unit().findAll(MethodCallExpr.class)) {
                if (Set.of("getConnection", "prepareStatement", "createStatement", "executeQuery", "executeUpdate")
                        .contains(call.getNameAsString())) {
                    violations.add(source.path() + ":"
                            + call.getBegin().map(p -> p.line).orElse(0) + " calls " + call.getNameAsString());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "WIRED tick workers must hand database work to a non-tick service:\n"
                        + String.join("\n", violations));
    }

    @Test
    void scheduledWorkRemainsInsideAuditedBoundedOwners() throws Exception {
        Map<String, Integer> callsBySource = new LinkedHashMap<>();
        for (Source source : wiredSources()) {
            int count = Math.toIntExact(source.unit().findAll(MethodCallExpr.class).stream()
                    .filter(call -> Set.of("schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay")
                            .contains(call.getNameAsString()))
                    .filter(call -> call.getScope().isPresent())
                    .count());
            if (count > 0) {
                callsBySource.put(source.path(), count);
            }
        }

        assertEquals(
                Map.of(
                        "com/eu/habbo/habbohotel/rooms/WiredGravityService.java", 1,
                        "com/eu/habbo/habbohotel/wired/core/WiredDelayedScheduler.java", 1,
                        "com/eu/habbo/habbohotel/wired/tick/WiredTickService.java", 1),
                callsBySource,
                "Every direct scheduler call needs an explicit bounded owner and lifecycle review");
    }

    private static List<Source> wiredSources() throws IOException {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        List<Source> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String relative = normalize(SOURCE_ROOT.relativize(source));
                if (WIRED_PREFIXES.stream().noneMatch(relative::startsWith) && !WIRED_ROOM_SOURCES.contains(relative)) {
                    continue;
                }
                sources.add(new Source(relative, StaticJavaParser.parse(source)));
            }
        }
        return sources;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record Source(String path, CompilationUnit unit) {}
}
