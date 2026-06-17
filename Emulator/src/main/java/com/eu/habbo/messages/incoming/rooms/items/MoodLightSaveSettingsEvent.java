package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionMoodLight;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomMoodlightData;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.MoodLightDataComposer;

import java.util.Arrays;
import java.util.List;

public class MoodLightSaveSettingsEvent extends MessageHandler {
    public static List<String> MOODLIGHT_AVAILABLE_COLORS = Arrays.asList("#74F5F5,#0053F7,#E759DE,#EA4532,#F2F851,#82F349,#000000".split(","));
    public static int MIN_BRIGHTNESS = (int) Math.floor(0.3 * 0xFF);

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        if ((room.getGuildId() <= 0 && room.getGuildRightLevel(this.client.getHabbo()).isLessThan(RoomRightLevels.GUILD_RIGHTS)) && !room.hasRights(this.client.getHabbo()))
            return;

        int id = this.packet.readInt();
        int backgroundOnly = this.packet.readInt();
        String color = this.packet.readString();
        int brightness = this.packet.readInt();
        boolean apply = this.packet.readBoolean();

        if (Emulator.getConfig().getBoolean("moodlight.color_check.enabled", true) && !MOODLIGHT_AVAILABLE_COLORS.contains(color)) {
            ScripterManager.scripterDetected(this.client, "User tried to set a moodlight to a non-whitelisted color: " + color);
            return;
        }

        if (brightness > 0xFF || brightness < MIN_BRIGHTNESS) {
            ScripterManager.scripterDetected(this.client, "User tried to set a moodlight's brightness to out-of-bounds ([76, 255]): " + brightness);
            return;
        }

        for (RoomMoodlightData data : room.getMoodlightData().valueCollection()) {
            if (data.getId() == id) {
                data.setBackgroundOnly(backgroundOnly == 2);
                data.setColor(color);
                data.setIntensity(brightness);
                if (apply) data.enable();

                for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionMoodLight.class)) {
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
        this.client.sendResponse(new MoodLightDataComposer(room.getMoodlightData()));
    }
}
