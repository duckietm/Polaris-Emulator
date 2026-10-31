package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionMoodLight;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomMoodlightData;
import com.eu.habbo.habbohotel.rooms.RoomPlayTestSupport;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class MoodLightTurnOnEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    /** The dimmer is switched and set up by who has rights in the room, or group rights in a group room. */
    static boolean mayControl(Room room, Habbo habbo) {
        return room.hasRights(habbo)
                || (room.getGuildId() > 0 && !room.getGuildRightLevel(habbo).isLessThan(RoomRightLevels.GUILD_RIGHTS));
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        // Playtest (the hotel's own rule beyond AIR): the dimmer is not switched, as for a visitor without rights.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getHabbo())) return;

        if (!mayControl(room, this.client.getHabbo())) return;

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
