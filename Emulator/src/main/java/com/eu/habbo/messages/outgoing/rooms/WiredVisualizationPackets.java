package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.WiredHighlight;
import com.eu.habbo.habbohotel.rooms.WiredUserTransparency;
import com.eu.habbo.habbohotel.rooms.WiredVisualizationEntry;
import com.eu.habbo.messages.ServerMessage;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** The shared pieces of the official wired highlight and transparency packets. */
final class WiredVisualizationPackets {
    static final int DEFAULT_MAXIMUM_ENTRIES = 1_000;
    static final int ABSOLUTE_MAXIMUM_ENTRIES = 5_000;

    private WiredVisualizationPackets() {}

    /** Distinct ids in ascending order, at most the configured number per packet. */
    static List<Integer> ids(Collection<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .limit(maximumEntries())
                .toList();
    }

    static <V> List<WiredVisualizationEntry<V>> entries(Collection<WiredVisualizationEntry<V>> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> entry != null && entry.value() != null)
                .limit(maximumEntries())
                .toList();
    }

    /** {@code type, strength, color, animation, easing}; brighten goes out at its full strength. */
    static void appendHighlight(ServerMessage message, WiredHighlight highlight) {
        message.appendInt(highlight.type());
        message.appendInt(highlight.wireStrength());
        message.appendInt(highlight.color());
        message.appendInt(highlight.animation());
        message.appendInt(highlight.easing());
    }

    /**
     * {@code [n, n x (id, transparency, click through, easing), m, m x (id, highlight)]}; the ids are furni ids
     * (by the id the client knows them by) when {@code furni}, room indices otherwise.
     */
    static void appendState(
            ServerMessage message,
            List<WiredVisualizationEntry<WiredUserTransparency>> transparency,
            List<WiredVisualizationEntry<WiredHighlight>> highlights,
            boolean furni) {
        message.appendInt(transparency.size());
        for (WiredVisualizationEntry<WiredUserTransparency> entry : transparency) {
            message.appendInt(BuildersClubItemIds.toWire(entry.id(), furni));
            message.appendInt(entry.value().transparency());
            message.appendBoolean(entry.value().clickThrough());
            message.appendInt(entry.value().easing());
        }
        message.appendInt(highlights.size());
        for (WiredVisualizationEntry<WiredHighlight> entry : highlights) {
            message.appendInt(BuildersClubItemIds.toWire(entry.id(), furni));
            appendHighlight(message, entry.value());
        }
    }

    private static int maximumEntries() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        int configured = configuration == null
                ? DEFAULT_MAXIMUM_ENTRIES
                : configuration.getInt("wired.visualization.max_entries_per_packet", DEFAULT_MAXIMUM_ENTRIES);
        return Math.max(1, Math.min(ABSOLUTE_MAXIMUM_ENTRIES, configured));
    }
}
