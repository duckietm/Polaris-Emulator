package polaris.messages.incoming.rooms.items;

import java.util.Arrays;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionMoodLight;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomMoodlightData;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.MoodLightDataComposer;

public class MoodLightSaveSettingsEvent extends MessageHandler {
    public static List<String> MOODLIGHT_AVAILABLE_COLORS =
            Arrays.asList("#74F5F5,#0053F7,#E759DE,#EA4532,#F2F851,#82F349,#000000".split(","));
    public static int MIN_BRIGHTNESS = (int) Math.floor(0.3 * 0xFF);

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // Playtest (the hotel's own rule beyond AIR): the dimmer is not set up, as for a visitor without rights.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) return;

        if (!MoodLightTurnOnEvent.mayControl(room, this.client.getUser())) return;

        int id = this.packet.readInt();
        int backgroundOnly = this.packet.readInt();
        String color = this.packet.readString();
        int brightness = this.packet.readInt();
        boolean apply = this.packet.readBoolean();

        if (Emulator.getConfig().getBoolean("moodlight.color_check.enabled", true)
                && !MOODLIGHT_AVAILABLE_COLORS.contains(color)) {
            ScripterManager.scripterDetected(
                    this.client, "User tried to set a moodlight to a non-whitelisted color: " + color);
            return;
        }

        if (brightness > 0xFF || brightness < MIN_BRIGHTNESS) {
            ScripterManager.scripterDetected(
                    this.client,
                    "User tried to set a moodlight's brightness to out-of-bounds ([76, 255]): " + brightness);
            return;
        }

        for (RoomMoodlightData data : room.getMoodlightData().values()) {
            if (data.getId() == id) {
                data.setBackgroundOnly(backgroundOnly == 2);
                data.setColor(color);
                data.setIntensity(brightness);
                if (apply) data.enable();

                for (UserItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionMoodLight.class)) {
                    item.setExtradata(data.toString());
                    item.needsUpdate(true);
                    room.updateItem(item);
                    Emulator.getThreading().run(item);
                }
            } else if (apply) {
                data.disable();
            }
        }

        room.setNeedsUpdate(true);
        this.client.sendResponse(MoodLightDataComposer.forRoom(room));
    }
}
