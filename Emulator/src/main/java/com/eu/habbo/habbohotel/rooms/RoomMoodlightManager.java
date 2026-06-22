package com.eu.habbo.habbohotel.rooms;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * Holds a room's moodlight presets. Extracted from {@link Room}: owns the
 * moodlight data map, parsing it from the {@code moodlight_data} DB column and
 * serializing it back. Behaviour is preserved verbatim from the previous inline
 * implementation (including the default presets and the id-renumbering done on
 * serialization).
 */
public class RoomMoodlightManager {
    private static final TIntObjectHashMap<RoomMoodlightData> defaultMoodData = new TIntObjectHashMap<>();

    static {
        for (int i = 1; i <= 3; i++) {
            RoomMoodlightData data = RoomMoodlightData.fromString("");
            data.setId(i);
            defaultMoodData.put(i, data);
        }
    }

    private final TIntObjectMap<RoomMoodlightData> moodlightData;

    /** Parse a room's {@code moodlight_data} column value into the preset map. */
    public RoomMoodlightManager(String moodlightDataColumn) {
        this.moodlightData = new TIntObjectHashMap<>(defaultMoodData);

        if (moodlightDataColumn == null) {
            return; // NULL column -> keep the default presets
        }

        for (String s : moodlightDataColumn.split(";")) {
            RoomMoodlightData data = RoomMoodlightData.fromString(s);
            this.moodlightData.put(data.getId(), data);
        }
    }

    public TIntObjectMap<RoomMoodlightData> getMoodlightData() {
        return this.moodlightData;
    }

    /**
     * Serialize the presets back to the {@code moodlight_data} column string,
     * renumbering ids 1..N (preserved exactly from the former {@code Room.save()}).
     */
    public String serialize() {
        StringBuilder moodLightData = new StringBuilder();

        int id = 1;
        for (RoomMoodlightData data : this.moodlightData.valueCollection()) {
            data.setId(id);
            moodLightData.append(data.toString()).append(";");
            id++;
        }

        return moodLightData.toString();
    }
}
