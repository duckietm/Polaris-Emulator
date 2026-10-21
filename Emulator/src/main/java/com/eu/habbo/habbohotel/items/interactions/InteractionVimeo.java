package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Vimeo display furni (official logic {@code furniture_vimeo}). The official client's Vimeo handler
 * reads the numeric {@code videoId} from the furni's map stuff data and plays that video when the furni
 * is opened, so this interaction carries exactly that one value.
 *
 * <p>Room rights holders set it through the generic set-object-data packet (the one room ads use); only
 * a positive number of at most {@link #MAX_VIDEO_ID_DIGITS} digits is kept, anything else restores the
 * previous id.
 */
public class InteractionVimeo extends InteractionCustomValues {
    public static final String VIDEO_ID_KEY = "videoId";
    public static final int MAX_VIDEO_ID_DIGITS = 12;

    public static final Map<String, String> defaultValues = new HashMap<String, String>() {
        {
            this.put(VIDEO_ID_KEY, "0");
        }
    };

    public InteractionVimeo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, defaultValues);
        this.keepOnlyVideoId(null);
    }

    public InteractionVimeo(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, defaultValues);
    }

    /** Whether {@code value} is a video id this furni accepts ({@code 0} means none). */
    public static boolean isValidVideoId(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_VIDEO_ID_DIGITS) return false;
        for (int i = 0; i < value.length(); i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') return false;
        }
        return true;
    }

    public String getVideoId() {
        return this.values.getOrDefault(VIDEO_ID_KEY, "0");
    }

    @Override
    public void onCustomValuesSaved(Room room, GameClient client, Map<String, String> oldValues) {
        if (!this.keepOnlyVideoId(oldValues)) return;

        // The save that called us already queued this item; the room's item saver persists the
        // corrected values with the next pass.
        this.setExtradata(this.toExtraData());
        this.needsUpdate(true);
        room.updateItem(this);
    }

    /** Drops every other key and an invalid id; returns whether anything changed. */
    private boolean keepOnlyVideoId(Map<String, String> previous) {
        boolean changed = false;
        synchronized (this.values) {
            String videoId = this.values.get(VIDEO_ID_KEY);
            if (!isValidVideoId(videoId)) {
                String fallback = previous == null ? null : previous.get(VIDEO_ID_KEY);
                videoId = isValidVideoId(fallback) ? fallback : "0";
                changed = true;
            }
            if (this.values.size() != 1) changed = true;
            this.values.clear();
            this.values.put(VIDEO_ID_KEY, videoId);
        }
        return changed;
    }
}
