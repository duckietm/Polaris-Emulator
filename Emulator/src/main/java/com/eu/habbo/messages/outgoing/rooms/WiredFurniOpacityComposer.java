package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Capability-gated, bounded room-furniture opacity update. */
public class WiredFurniOpacityComposer extends MessageComposer {
    private static final int HEADER = 5109;
    public static final int PROTOCOL_VERSION = 1;
    static final int DEFAULT_MAXIMUM_UPDATES = 1_000;
    static final int ABSOLUTE_MAXIMUM_UPDATES = 5_000;

    private final int roomId;
    private final int easing;
    private final int durationMs;
    private final List<WiredOpacityState> updates;

    public WiredFurniOpacityComposer(int roomId, Collection<WiredOpacityState> updates, int easing, int durationMs) {
        this.roomId = Math.max(0, roomId);
        this.easing = Math.max(0, Math.min(4, easing));
        this.durationMs = Math.max(0, Math.min(10_000, durationMs));

        Map<Integer, WiredOpacityState> unique = new LinkedHashMap<>();
        if (updates != null) {
            updates.stream()
                    .filter(update -> update != null && update.itemId() > 0)
                    .sorted(Comparator.comparingInt(WiredOpacityState::itemId))
                    .limit(configuredMaximumUpdates())
                    .forEach(update -> unique.put(update.itemId(), update));
        }
        this.updates = List.copyOf(unique.values());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        this.response.appendInt(PROTOCOL_VERSION);
        this.response.appendInt(this.roomId);
        this.response.appendInt(this.updates.size());
        for (WiredOpacityState update : this.updates) {
            this.response.appendInt(update.itemId());
            this.response.appendBoolean(update.wallItem());
            this.response.appendInt(update.opacity());
            this.response.appendBoolean(update.clickThrough());
            this.response.appendInt(this.easing);
            this.response.appendInt(this.durationMs);
        }
        return this.response;
    }

    private static int configuredMaximumUpdates() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        int configured = configuration == null
                ? DEFAULT_MAXIMUM_UPDATES
                : configuration.getInt("wired.opacity.max_updates_per_packet", DEFAULT_MAXIMUM_UPDATES);
        return Math.max(1, Math.min(ABSOLUTE_MAXIMUM_UPDATES, configured));
    }
}
