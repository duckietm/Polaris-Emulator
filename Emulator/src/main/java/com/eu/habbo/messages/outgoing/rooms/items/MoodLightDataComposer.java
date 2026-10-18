package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionMoodLight;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomMoodlightData;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

/**
 * The dimmer presets (AIR {@code ?_-J2Y?/?_-DL?}): preset count, selected preset, the presets, then whether the
 * dimmer is on and its item id.
 */
public class MoodLightDataComposer extends MessageComposer {
    private final Collection<RoomMoodlightData> moodLightData;
    private final boolean isOn;
    private final int itemId;

    /** Kept for plugins; reports the dimmer off with item id 0. */
    public MoodLightDataComposer(Collection<RoomMoodlightData> moodLightData) {
        this(moodLightData, false, 0);
    }

    public MoodLightDataComposer(Collection<RoomMoodlightData> moodLightData, boolean isOn, int itemId) {
        this.moodLightData = moodLightData;
        this.isOn = isOn;
        this.itemId = itemId;
    }

    /** The presets of {@code room}, with the on state and id of its dimmer (the first one, as rooms hold one). */
    public static MoodLightDataComposer forRoom(Room room) {
        HabboItem dimmer = room.getRoomSpecialTypes().getItemsOfType(InteractionMoodLight.class).stream()
                .findFirst()
                .orElse(null);
        boolean isOn = dimmer != null && isEnabled(dimmer.getExtradata());
        return new MoodLightDataComposer(room.getMoodlightData().values(), isOn, dimmer != null ? dimmer.getId() : 0);
    }

    /** Whether dimmer extradata ({@code enabled ? 2 : 1, preset, type, colour, intensity}) says it is on. */
    static boolean isEnabled(String extradata) {
        if (extradata == null || extradata.split(",").length != 5) return false;
        try {
            return RoomMoodlightData.fromString(extradata).isEnabled();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MoodLightDataComposer);
        this.response.appendInt(3); // PresetCount

        int index = 1;
        for (RoomMoodlightData data : this.moodLightData) {
            if (data.isEnabled()) {
                this.response.appendInt(data.getId());
                index = -1;
                break;
            }
            index++;
        }

        if (index != -1) {
            this.response.appendInt(1);
        }

        int i = 1;
        for (RoomMoodlightData data : this.moodLightData) {
            this.response.appendInt(data.getId()); // Preset ID
            this.response.appendInt(data.isBackgroundOnly() ? 2 : 1); // Background only ? 2 : 1
            this.response.appendString(data.getColor()); // Color
            this.response.appendInt(data.getIntensity()); // Intensity
            i++;
        }

        for (; i <= 3; i++) {
            this.response.appendInt(i);
            this.response.appendInt(1);
            this.response.appendString("#000000");
            this.response.appendInt(255);
        }

        this.response.appendBoolean(this.isOn);
        this.response.appendInt(this.itemId);

        return this.response;
    }

    public Collection<RoomMoodlightData> getMoodLightData() {
        return moodLightData;
    }

    public boolean isOn() {
        return isOn;
    }

    public int getItemId() {
        return itemId;
    }
}
