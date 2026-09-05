package com.eu.habbo.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every wired packet handler has to pass through an authorization gate.
 *
 * <p>Wired is automation one user writes and the server runs for another, so the write path is the
 * boundary that matters. Today it holds — the three save handlers call {@code canModifyWired}, the
 * settings handler {@code canManageWiredSettings}, and the variable snapshot checks {@code
 * canInspectWired} inside the manager it delegates to. What is missing is anything that keeps it
 * holding: a new handler copied from the shape that delegates would look perfectly correct and
 * check nothing at all.
 *
 * <p>So the rule is the gate must be reachable from the handler, either called there or called by
 * the single collaborator it hands the room to. A handler that needs neither says so by name below,
 * which makes each exemption a decision somebody wrote down.
 */
class WiredHandlerAuthorizationTest {

    private static final Path HANDLER_ROOT = Path.of("src/main/java/com/eu/habbo/messages/incoming/wired");

    /** Anything that answers this question is an authorization gate for our purposes. */
    private static final List<String> GATES =
            List.of("canModifyWired", "canManageWiredSettings", "canInspectWired", "hasRights", "hasPermission");

    /**
     * Not handlers, or handlers that legitimately need no gate.
     *
     * <p>{@code WiredFeatureCapabilitiesEvent} answers what the server supports, which is the same
     * for everyone and reveals nothing about a room. The rest are exceptions, policies and adapters
     * rather than packet entry points.
     */
    private static final Set<String> EXEMPT = Set.of(
            "WiredFeatureCapabilitiesEvent.java",
            "WiredConditionSaveAdapter.java",
            "WiredFurniRuntimeStatePolicy.java",
            "WiredUserInspectMovePolicy.java",
            "WiredSaveException.java",
            "WiredTriggerSaveException.java");

    @Test
    void everyWiredHandlerReachesAnAuthorizationGate() throws Exception {
        List<String> ungated = new ArrayList<>();

        try (Stream<Path> sources = Files.list(HANDLER_ROOT)) {
            for (Path source :
                    sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = source.getFileName().toString();
                if (EXEMPT.contains(name)) {
                    continue;
                }

                if (!reachesGate(source)) {
                    ungated.add(name);
                }
            }
        }

        assertTrue(
                ungated.isEmpty(),
                "Wired packet handlers with no reachable authorization gate: " + ungated
                        + ". Call one of " + GATES + " in the handler, or in the single collaborator it"
                        + " delegates the room to, or add the file to EXEMPT with a reason.");
    }

    /** True when the handler calls a gate itself, or hands the room to something that does. */
    private static boolean reachesGate(Path handler) throws IOException {
        String source = Files.readString(handler);
        if (GATES.stream().anyMatch(source::contains)) {
            return true;
        }

        for (String collaborator : delegates(source)) {
            Path target = findSource(collaborator);
            if (target != null
                    && GATES.stream().anyMatch(gate -> readQuietly(target).contains(gate))) {
                return true;
            }
        }

        return false;
    }

    /**
     * What the handler hands the room to.
     *
     * <p>Two shapes carry the gate elsewhere and both are legitimate: a manager fetched off the room
     * ({@code room.getUserVariableManager()}), and a composer built from it ({@code new
     * WiredRoomSettingsDataComposer(room, habbo)}, which zeroes the masks a viewer may not inspect).
     */
    private static List<String> delegates(String source) {
        List<String> names = new ArrayList<>();
        for (String pattern : List.of("room\\.get(\\w+)\\(\\)", "new (\\w+)\\(\\s*room\\b")) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile(pattern).matcher(source);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static Path findSource(String typeHint) {
        Path root = Path.of("src/main/java");
        try (Stream<Path> all = Files.walk(root)) {
            return all.filter(p -> p.getFileName().toString().equals(typeHint + ".java"))
                    .findFirst()
                    .orElseGet(() -> findByRoomPrefix(typeHint));
        } catch (IOException e) {
            return null;
        }
    }

    /** {@code getUserVariableManager()} lives on a differently named class; match on the suffix. */
    private static Path findByRoomPrefix(String typeHint) {
        Path rooms = Path.of("src/main/java/com/eu/habbo/habbohotel/rooms");
        try (Stream<Path> all = Files.list(rooms)) {
            return all.filter(p -> p.getFileName().toString().endsWith(typeHint + ".java"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}
