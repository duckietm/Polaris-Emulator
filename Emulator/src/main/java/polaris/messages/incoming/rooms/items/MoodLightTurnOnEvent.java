package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionMoodLight;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomMoodlightData;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomRightLevels;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class MoodLightTurnOnEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    /** The dimmer is switched and set up by who has rights in the room, or group rights in a group room. */
    static boolean mayControl(Room room, User user) {
        return room.hasRights(user)
                || (room.getGuildId() > 0 && !room.getGuildRightLevel(user).isLessThan(RoomRightLevels.GUILD_RIGHTS));
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // Playtest (the hotel's own rule beyond AIR): the dimmer is not switched, as for a visitor without rights.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) return;

        if (!mayControl(room, this.client.getUser())) return;

        for (UserItem moodLight : room.getRoomSpecialTypes().getItemsOfType(InteractionMoodLight.class)) {
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
