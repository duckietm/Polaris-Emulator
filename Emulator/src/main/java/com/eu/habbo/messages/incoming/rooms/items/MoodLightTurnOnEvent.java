package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionMoodLight;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomMoodlightData;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class MoodLightTurnOnEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if ((room.getGuildId() > 0 && room.getGuildRightLevel(this.client.getHabbo()).isLessThan(RoomRightLevels.GUILD_RIGHTS)) && !room.hasRights(this.client.getHabbo()))
            return;

        for (HabboItem moodLight : room.getRoomSpecialTypes().getItemsOfType(InteractionMoodLight.class)) {
            // enabled ? 2 : 1, preset id, background only ? 2 : 1, color, intensity

            String extradata = "2,1,2,#FF00FF,255";
            for (RoomMoodlightData data : room.getMoodlightData().values()) {
                if (data.isEnabled()) {
                    extradata = data.toString();
                    break;
                }
            }

            RoomMoodlightData adjusted = RoomMoodlightData.fromString(extradata);
            if (RoomMoodlightData.fromString(moodLight.getExtradata()).isEnabled()) adjusted.disable();
            moodLight.setExtradata(adjusted.toString());

            moodLight.needsUpdate(true);
            room.updateItem(moodLight);
            Emulator.getThreading().run(moodLight);
        }
    }
}
