package polaris.hotel.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * A bundle that names catalogue offers instead of furni. In {@code catalog_items.item_ids} an entry {@code @123} (or
 * {@code @123:2} for two of it) stands for what offer 123 sells; plain furni entries ({@code 4501}, {@code 4501:2})
 * may stand beside it. The bundle then follows that offer: change the offer's furni and every bundle that names it
 * changes with it, so a furni is set up once.
 *
 * <p>An offer that is named may itself name offers; a loop or a missing offer is left out and reported.
 */
public final class BundleOfferReferences {
    /** Marks an entry as a catalogue offer id. */
    public static final char MARKER = '@';

    /** How deep offers may name offers. */
    static final int MAX_DEPTH = 5;

    /** The most of one furni a bundle hands out, whatever the amounts multiply to. */
    static final int MAX_AMOUNT = 1000;

    /** What a named offer sells: its own {@code item_ids} and {@code amount}. */
    public record Content(String itemIds, int amount) {}

    /**
     * A bundle's contents written as furni only.
     *
     * @param itemIds {@code id:amount;} for each furni, in the order they were first named; empty when nothing is left
     * @param missing the offer ids that could not be used (unknown, or part of a loop)
     */
    public record Expansion(String itemIds, List<Integer> missing) {}

    private BundleOfferReferences() {}

    /** Whether the item list names at least one offer. */
    public static boolean hasReferences(String itemIds) {
        return itemIds != null && itemIds.indexOf(MARKER) >= 0;
    }

    /**
     * Writes a bundle's item list as furni only.
     *
     * @param itemIds the bundle's {@code item_ids}
     * @param amount the bundle offer's own {@code amount}: what a single plain furni entry without an amount counts
     * @param lookup the offer with an id, or null when there is none
     */
    public static Expansion expand(String itemIds, int amount, IntFunction<Content> lookup) {
        Map<Integer, Long> furni = new LinkedHashMap<>();
        List<Integer> missing = new ArrayList<>();

        collect(itemIds, amount, 1L, new HashSet<>(), 0, lookup, furni, missing);

        StringBuilder text = new StringBuilder();
        furni.forEach((id, count) ->
                text.append(id).append(':').append(Math.min(MAX_AMOUNT, count)).append(';'));

        return new Expansion(text.toString(), List.copyOf(missing));
    }

    private static void collect(
            String itemIds,
            int offerAmount,
            long times,
            Set<Integer> path,
            int depth,
            IntFunction<Content> lookup,
            Map<Integer, Long> furni,
            List<Integer> missing) {
        if (itemIds == null || itemIds.isBlank()) return;

        // A lone furni entry counts the offer's amount; in a list an entry without an amount counts one.
        boolean single = itemIds.indexOf(';') < 0;

        for (String raw : itemIds.split(";")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;

            boolean offer = token.charAt(0) == MARKER;
            String[] parts = (offer ? token.substring(1) : token).split(":", -1);

            int id;
            int count;
            try {
                id = Integer.parseInt(parts[0].trim());
                count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : (!offer && single ? offerAmount : 1);
            } catch (NumberFormatException e) {
                continue;
            }
            if (id <= 0 || count <= 0 || parts.length > 2) continue;

            if (!offer) {
                furni.merge(id, count * times, (a, b) -> Math.min((long) MAX_AMOUNT, a + b));
                continue;
            }

            Content content = depth >= MAX_DEPTH || path.contains(id) ? null : lookup.apply(id);
            if (content == null) {
                if (!missing.contains(id)) missing.add(id);
                continue;
            }

            path.add(id);
            collect(
                    content.itemIds(),
                    content.amount(),
                    Math.min((long) MAX_AMOUNT, times * count),
                    path,
                    depth + 1,
                    lookup,
                    furni,
                    missing);
            path.remove(id);
        }
    }
}
