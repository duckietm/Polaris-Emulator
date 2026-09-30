package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every acc_* / cmd_* key the code checks must be defined by a migration. A key nobody defines is
 * denied to every rank, so a typo or a forgotten migration silently turns a feature off
 * (acc_no_mute, cmd_roomfx and cmd_dance all did).
 */
class PermissionKeysDefinedContractTest {
    private static final Pattern KEY_IN_CODE = Pattern.compile("\"((?:acc|cmd)_[a-z0-9_]+)\"");
    private static final Pattern KEY_IN_MIGRATION = Pattern.compile("['`]((?:acc|cmd)_[a-z0-9_]+)['`]");

    @Test
    void everyPermissionKeyTheCodeChecksIsDefined() throws IOException {
        Map<String, Set<String>> used = new TreeMap<>();

        for (Path file : files(Path.of("src/main/java"), ".java")) {
            Matcher matcher = KEY_IN_CODE.matcher(read(file));
            while (matcher.find()) {
                used.computeIfAbsent(matcher.group(1), key -> new TreeSet<>())
                        .add(file.getFileName().toString());
            }
        }

        Set<String> defined = new TreeSet<>();
        for (Path file : files(Path.of("src/main/resources/db/migration"), ".sql")) {
            Matcher matcher = KEY_IN_MIGRATION.matcher(read(file));
            while (matcher.find()) {
                defined.add(matcher.group(1));
            }
        }

        assertFalse(used.isEmpty(), "no permission keys found in the code; the scan is broken");
        assertTrue(
                defined.contains("acc_anyroomowner"), "no permission keys found in the migrations; the scan is broken");

        Map<String, Set<String>> missing = new TreeMap<>(used);
        missing.keySet().removeAll(defined);

        assertTrue(
                missing.isEmpty(),
                "Permission keys checked in code but defined by no migration (add them to permission_definitions): "
                        + missing);
    }

    private static Set<Path> files(Path root, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(suffix))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
