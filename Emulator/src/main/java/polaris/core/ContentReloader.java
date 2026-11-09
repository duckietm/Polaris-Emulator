package polaris.core;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloads hotel content from the database while the hotel runs: the work of the in-game {@code :update_*} commands,
 * reachable without being logged in (RCON {@code reloadcontent}, used by the All-in-1-Manager). Each target runs exactly
 * what its command runs. Installed at start-up; until then nothing can be reloaded.
 */
public final class ContentReloader {
    private static final AtomicReference<ContentReloader> INSTALLED = new AtomicReference<>();

    private final Map<String, Runnable> targets;

    public ContentReloader(Map<String, Runnable> targets) {
        Map<String, Runnable> copy = new TreeMap<>();
        Objects.requireNonNull(targets, "targets")
                .forEach((name, reload) -> copy.put(normalize(name), Objects.requireNonNull(reload, name)));
        this.targets = Collections.unmodifiableMap(copy);
    }

    public static void install(ContentReloader reloader) {
        INSTALLED.set(Objects.requireNonNull(reloader, "reloader"));
    }

    /** Removes the installed reloader (tests). */
    public static void uninstall() {
        INSTALLED.set(null);
    }

    /** The installed reloader, or null before start-up finished. */
    public static ContentReloader installed() {
        return INSTALLED.get();
    }

    /** The target names, sorted. */
    public Set<String> targets() {
        return this.targets.keySet();
    }

    public boolean has(String target) {
        return target != null && this.targets.containsKey(normalize(target));
    }

    /** Reloads one target; false when there is no such target. */
    public boolean reload(String target) {
        Runnable reload = target == null ? null : this.targets.get(normalize(target));
        if (reload == null) return false;
        reload.run();
        return true;
    }

    /** "Pet_Data", "pet data" and "petdata" name the same target, as RCON command keys do. */
    static String normalize(String name) {
        return name.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace(" ", "")
                .replace("-", "");
    }
}
