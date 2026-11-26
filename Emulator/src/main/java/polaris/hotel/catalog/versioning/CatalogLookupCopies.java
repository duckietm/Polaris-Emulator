package polaris.hotel.catalog.versioning;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only copies of the catalogue's lookup tables (item definition ids, currency types, limited sells).
 *
 * <p>Not {@code Set.copyOf} / {@code Map.copyOf}: their compact tables probe linearly, and the ids of a large
 * catalogue sit in dense blocks, so copying and looking up 80,000 of them took seconds on every Catalog Studio
 * save. A hashed copy does the same in milliseconds. Like the {@code copyOf} copies these reject nulls and cannot
 * be changed.
 */
final class CatalogLookupCopies {
    private CatalogLookupCopies() {}

    // Checked while copying: contains(null) itself throws on some sets.
    static <T> Set<T> set(Set<T> source) {
        Set<T> copy = new HashSet<>(Math.max(16, (int) (source.size() / 0.75f) + 1));
        for (T element : source) copy.add(Objects.requireNonNull(element, "null element"));
        return Collections.unmodifiableSet(copy);
    }

    static <K, V> Map<K, V> map(Map<K, V> source) {
        Map<K, V> copy = new HashMap<>(Math.max(16, (int) (source.size() / 0.75f) + 1));
        for (Map.Entry<K, V> entry : source.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "null key"),
                    Objects.requireNonNull(entry.getValue(), "null value"));
        }
        return Collections.unmodifiableMap(copy);
    }
}
