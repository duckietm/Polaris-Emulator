package polaris.plugin.remap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The names plugin jars compiled before the rename link against, and what each became.
 *
 * <p>The table lives in {@code polaris/plugin/legacy-names.txt}: package prefixes (longest first),
 * renamed types, renamed methods and renamed constants. All names are internal names ({@code a/b/C}).
 */
public final class LegacyNames {

    private static final String RESOURCE = "/polaris/plugin/legacy-names.txt";

    private final List<String[]> packages = new ArrayList<>();
    private final Map<String, String> types = new HashMap<>();
    private final Map<String, String> typesBack = new HashMap<>();
    private final Map<String, String> methods = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();
    private final Set<String> renamedFieldNames = new HashSet<>();

    /** Loaded on first use, by the class initialisation of the holder. */
    private static final class Standard {
        private static final LegacyNames NAMES = load();
    }

    /** The table shipped in the emulator jar. */
    public static LegacyNames standard() {
        return Standard.NAMES;
    }

    private static LegacyNames load() {
        try (InputStream input = LegacyNames.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Builds a table from lines in the resource's format. */
    public static LegacyNames parse(List<String> lines) {
        LegacyNames names = new LegacyNames();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] part = line.split("\\s+");
            switch (part[0]) {
                case "package" -> names.packages.add(new String[] {part[1], part[2]});
                case "type" -> {
                    names.types.put(part[1], part[2]);
                    names.typesBack.put(part[2], part[1]);
                }
                case "method" -> names.methods.put(part[1], part[2]);
                case "field" -> {
                    names.fields.put(part[1] + "." + part[2], part[3]);
                    names.renamedFieldNames.add(part[2]);
                }
                default -> throw new IllegalArgumentException("Unknown legacy name entry: " + line);
            }
        }
        names.packages.sort(
                Comparator.comparingInt((String[] rule) -> rule[0].length()).reversed());
        return names;
    }

    private LegacyNames() {}

    /** The current internal name of a type a plugin was compiled against. */
    public String mapType(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        String renamed = this.types.get(internalName);
        if (renamed != null) {
            return renamed;
        }
        int nested = internalName.indexOf('$');
        if (nested > 0) {
            String outer = internalName.substring(0, nested);
            String mappedOuter = this.mapType(outer);
            if (!mappedOuter.equals(outer)) {
                return mappedOuter + internalName.substring(nested);
            }
        }
        for (String[] rule : this.packages) {
            if (internalName.startsWith(rule[0])) {
                return rule[1] + internalName.substring(rule[0].length());
            }
        }
        return internalName;
    }

    /** The old internal name a current name was moved from, or {@code null} when it never moved. */
    public String unmapType(String internalName) {
        String renamed = this.typesBack.get(internalName);
        if (renamed != null) {
            return renamed;
        }
        int nested = internalName.indexOf('$');
        if (nested > 0) {
            String outer = this.unmapType(internalName.substring(0, nested));
            if (outer != null) {
                return outer + internalName.substring(nested);
            }
        }
        for (String[] rule : this.packages) {
            if (internalName.startsWith(rule[1])) {
                String old = rule[0] + internalName.substring(rule[1].length());
                if (this.mapType(old).equals(internalName)) {
                    return old;
                }
            }
        }
        return null;
    }

    /** The current name of a renamed method, or {@code null} when the name did not change. */
    public String renamedMethod(String name) {
        return this.methods.get(name);
    }

    /** Whether some emulator class renamed a field that had this name. */
    public boolean isRenamedField(String name) {
        return this.renamedFieldNames.contains(name);
    }

    /** The current name of a field of a current owner, or the name itself when it did not change. */
    public String mapField(String currentOwner, String name) {
        return this.fields.getOrDefault(currentOwner + "." + name, name);
    }

    /** Whether an internal name belongs to the emulator, before or after the rename. */
    public boolean isEmulatorType(String internalName) {
        return internalName.startsWith("polaris/")
                || !this.mapType(internalName).equals(internalName);
    }
}
