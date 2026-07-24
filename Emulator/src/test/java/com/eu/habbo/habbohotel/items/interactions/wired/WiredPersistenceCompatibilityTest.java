package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.Room;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/**
 * Characterizes persisted-data handling across every registered wired implementation. This is a
 * synthetic compatibility matrix, not a replacement for the sanitized real-hotel corpus required
 * by the modernization exit gate.
 *
 * <p>Regeneration is an explicit review action:
 *
 * <pre>
 * mvn test -Dtest=WiredPersistenceCompatibilityTest \
 *   -Dpolaris.wired.persistence.regenerate=true
 * </pre>
 */
class WiredPersistenceCompatibilityTest {

    private static final String REGENERATE_PROPERTY = "polaris.wired.persistence.regenerate";
    private static final Path CONTRACT =
            Path.of("src", "test", "resources", "wired-compatibility", "persistence-matrix-v1.txt");
    private static final Path ITEM_MANAGER =
            Path.of("src", "main", "java", "com", "eu", "habbo", "habbohotel", "items", "ItemManager.java");
    private static final Pattern IMPORT = Pattern.compile("import\\s+([A-Za-z0-9_$.]+);", Pattern.MULTILINE);
    private static final Pattern INTERACTION = Pattern.compile(
            "new\\s+ItemInteraction\\(\\s*\"(wf_[^\"]+)\"\\s*,\\s*([A-Za-z0-9_$.]+)\\.class\\s*\\)", Pattern.MULTILINE);
    private static final Pattern FAILURE_ROW = Pattern.compile("^CASE (\\S+) ERROR (\\S+)$");

    private static final Map<String, String> PAYLOADS = Map.of(
            "blank", "",
            "json-empty", "{}",
            "json-malformed", "{broken",
            "legacy-zero", "0",
            "legacy-tab", "1\\tfixture");

    @Test
    void registeredWiredPersistenceBehaviorStaysStable() throws Exception {
        List<String> actual = snapshot();
        if (System.getProperty(REGENERATE_PROPERTY) != null) {
            Files.createDirectories(CONTRACT.getParent());
            Files.write(CONTRACT, actual, StandardCharsets.UTF_8);
            return;
        }

        assertTrue(
                Files.isRegularFile(CONTRACT),
                "Missing wired persistence contract; regenerate with -D" + REGENERATE_PROPERTY + "=true");
        assertEquals(
                Files.readAllLines(CONTRACT, StandardCharsets.UTF_8),
                actual,
                "Persisted wired-data behavior changed. Preserve legacy reads and semantic state, or "
                        + "review this fixture as an explicit compatibility or hardening change.");
    }

    @Test
    void matrixCoversEveryRegisteredInteractionWiredClass() throws Exception {
        Set<Class<? extends InteractionWired>> types = wiredTypes();
        assertEquals(235, types.size(), "Review every added or removed registered wired persistence type");
    }

    @Test
    void remainingSyntheticFailuresHaveReviewedTaxonomy() throws Exception {
        Map<String, Integer> byPayload = new HashMap<>();
        Map<String, Integer> byException = new HashMap<>();

        for (String line : Files.readAllLines(CONTRACT, StandardCharsets.UTF_8)) {
            Matcher matcher = FAILURE_ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            byPayload.merge(matcher.group(1), 1, Integer::sum);
            byException.merge(matcher.group(2), 1, Integer::sum);
        }

        assertEquals(
                Map.of(
                        "blank", 19,
                        "json-empty", 21,
                        "json-malformed", 97,
                        "legacy-zero", 12,
                        "legacy-tab", 21),
                byPayload,
                "Review every change against the production-loader boundary and real legacy corpus");
        assertEquals(
                Map.of(
                        "java.io.EOFException", 84,
                        "java.lang.ArrayIndexOutOfBoundsException", 2,
                        "java.lang.NullPointerException", 64,
                        "java.lang.NumberFormatException", 20),
                byException,
                "Unexpected failure classes must not enter the persisted-data quarantine boundary");
    }

    private static List<String> snapshot() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# Polaris wired persisted-data behavior matrix v1");
        lines.add("# Synthetic ResultSet fixtures; no user or hotel data is stored here.");
        lines.add("# OUT is Base64 UTF-8. ERROR records current load/serialize failure behavior.");
        lines.add("");

        for (Class<? extends InteractionWired> type : wiredTypes().stream()
                .sorted(Comparator.comparing(Class::getName))
                .toList()) {
            lines.add("TYPE " + type.getName());
            for (Map.Entry<String, String> payload : orderedPayloads()) {
                lines.add("CASE " + payload.getKey() + " " + outcome(type, payload.getValue()));
            }
            lines.add("");
        }
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static List<Map.Entry<String, String>> orderedPayloads() {
        return List.of(
                Map.entry("blank", PAYLOADS.get("blank")),
                Map.entry("json-empty", PAYLOADS.get("json-empty")),
                Map.entry("json-malformed", PAYLOADS.get("json-malformed")),
                Map.entry("legacy-zero", PAYLOADS.get("legacy-zero")),
                Map.entry("legacy-tab", PAYLOADS.get("legacy-tab")));
    }

    private static String outcome(Class<? extends InteractionWired> type, String payload) {
        try {
            InteractionWired item = instantiate(type);
            ResultSet resultSet = resultSet(payload);
            Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);
            item.loadWiredData(resultSet, room);
            String serialized = item.getWiredData();
            String encoded = Base64.getEncoder()
                    .encodeToString((serialized == null ? "<null>" : serialized).getBytes(StandardCharsets.UTF_8));
            return encoded.isEmpty() ? "OUT" : "OUT " + encoded;
        } catch (Throwable failure) {
            Throwable root = rootCause(failure);
            return "ERROR " + root.getClass().getName();
        }
    }

    private static InteractionWired instantiate(Class<? extends InteractionWired> type) throws Exception {
        Constructor<? extends InteractionWired> constructor =
                type.getConstructor(int.class, int.class, Item.class, String.class, int.class, int.class);
        Item baseItem = mock(Item.class);
        when(baseItem.getSpriteId()).thenReturn(123);
        when(baseItem.getName()).thenReturn("wired_fixture");
        return constructor.newInstance(4242, 7, baseItem, "0", 0, 0);
    }

    private static ResultSet resultSet(String payload) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            String column = invocation.getArgument(0);
            return "wired_data".equals(column) ? payload : "";
        });
        return resultSet;
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends InteractionWired>> wiredTypes() throws Exception {
        String source = Files.readString(ITEM_MANAGER, StandardCharsets.UTF_8);
        Map<String, String> imports = imports(source);
        Matcher matcher = INTERACTION.matcher(source);
        Set<Class<? extends InteractionWired>> result = new LinkedHashSet<>();
        while (matcher.find()) {
            Class<?> type = resolveType(imports, matcher.group(2));
            if (InteractionWired.class.isAssignableFrom(type)) {
                result.add((Class<? extends InteractionWired>) type);
            }
        }
        return result;
    }

    private static Map<String, String> imports(String source) {
        Map<String, String> imports = new LinkedHashMap<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            imports.put(name.substring(name.lastIndexOf('.') + 1), name);
        }
        return imports;
    }

    private static Class<?> resolveType(Map<String, String> imports, String sourceName) throws ClassNotFoundException {
        int nestedSeparator = sourceName.indexOf('.');
        String outerName = nestedSeparator < 0 ? sourceName : sourceName.substring(0, nestedSeparator);
        String fullyQualifiedOuter = imports.get(outerName);
        if (fullyQualifiedOuter == null) {
            throw new ClassNotFoundException("No import found for registered type " + sourceName);
        }
        String nestedName = nestedSeparator < 0 ? "" : "$" + sourceName.substring(nestedSeparator + 1);
        return Class.forName(fullyQualifiedOuter + nestedName);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable result = failure;
        if (result instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            result = invocation.getCause();
        }
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }
}
