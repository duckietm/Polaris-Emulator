package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.RoomMoodlightData;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collection;

public class MoodLightDataComposer extends MessageComposer {
    private final Collection<RoomMoodlightData> moodLightData;

    public MoodLightDataComposer(Collection<RoomMoodlightData> moodLightData) {
        this.moodLightData = moodLightData;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MoodLightDataComposer);
        this.response.appendInt(3); //PresetCount

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
            this.response.appendInt(data.getId()); //Preset ID
            this.response.appendInt(data.isBackgroundOnly() ? 2 : 1); //Background only ? 2 : 1
            this.response.appendString(data.getColor()); //Color
            this.response.appendInt(data.getIntensity()); //Intensity
            i++;
        }

        for (; i <= 3; i++) {
            this.response.appendInt(i);
            this.response.appendInt(1);
            this.response.appendString("#000000");
            this.response.appendInt(255);
        }


        //:test 2780 i:1 i:1 i:1 i:2 s:#FF00FF i:255
        return this.response;
    }

    public Collection<RoomMoodlightData> getMoodLightData() {
        return moodLightData;
    }
}
