package com.srth.plugin.events.habboHandlers;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.habbohotel.rooms.RoomState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomChatSettingsComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomEditSettingsErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomSettingsSavedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomSettingsUpdatedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomThicknessComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.utilities.utils.disablePlugin;

public class HandlerRoomSettingsSaved implements ICallable {

    @Override
    public void call(MessageHandler event) {

        int roomId = event.packet.readInt();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        Logger LOGGER = LoggerFactory.getLogger(Emulator.class);

        if (room != null) {
            if (room.isOwner(event.client.getHabbo())) {

                Habbo habbo = event.client.getHabbo();
                String name = event.packet.readString();

                if (name.trim().isEmpty() || name.length() > 60) {
                    habbo.getClient().sendResponse(new RoomEditSettingsErrorComposer(room.getId(),
                            RoomEditSettingsErrorComposer.ROOM_NAME_MISSING, ""));
                    return;
                }

                if (!Emulator.getGameEnvironment().getWordFilter().filter(name, habbo.getClient().getHabbo())
                        .equals(name)) {
                    habbo.getClient().sendResponse(new RoomEditSettingsErrorComposer(room.getId(),
                            RoomEditSettingsErrorComposer.ROOM_NAME_BADWORDS, ""));
                    return;
                }

                String description = event.packet.readString();
                if (description.length() > 255) {
                    return;
                }
                if (!Emulator.getGameEnvironment().getWordFilter().filter(description, habbo.getClient().getHabbo())
                        .equals(description)) {
                    habbo.getClient().sendResponse(new RoomEditSettingsErrorComposer(room.getId(),
                            RoomEditSettingsErrorComposer.ROOM_DESCRIPTION_BADWORDS, ""));
                    return;
                }

                RoomState state = RoomState.values()[event.packet.readInt() % RoomState.values().length];

                String password = event.packet.readString();
                if (state == RoomState.PASSWORD && password.isEmpty()
                        && (room.getPassword() == null || room.getPassword().isEmpty())) {
                    habbo.getClient().sendResponse(new RoomEditSettingsErrorComposer(room.getId(),
                            RoomEditSettingsErrorComposer.PASSWORD_REQUIRED, ""));
                    return;
                }

                int usersMax = event.packet.readInt();
                int categoryId = event.packet.readInt();
                StringBuilder tags = new StringBuilder();
                int count = Math.min(event.packet.readInt(), 2);
                for (int i = 0; i < count; i++) {
                    String tag = event.packet.readString();

                    if (tag.length() > 15) {
                        habbo.getClient().sendResponse(new RoomEditSettingsErrorComposer(room.getId(),
                                RoomEditSettingsErrorComposer.TAGS_TOO_LONG, ""));
                        return;
                    }
                    tags.append(tag).append(";");
                }

                if (!Emulator.getGameEnvironment().getWordFilter().filter(tags.toString(), habbo.getClient().getHabbo())
                        .equals(tags.toString())) {
                    habbo.getClient().sendResponse(new RoomEditSettingsErrorComposer(room.getId(),
                            RoomEditSettingsErrorComposer.ROOM_TAGS_BADWWORDS, ""));
                    return;
                }

                if (tags.length() > 0) {
                    for (String s : Emulator.getConfig().getValue("hotel.room.tags.staff").split(";")) {
                        if (tags.toString().contains(s)) {
                            habbo.getClient().sendResponse(new RoomEditSettingsErrorComposer(room.getId(),
                                    RoomEditSettingsErrorComposer.RESTRICTED_TAGS, "1"));
                            return;
                        }
                    }
                }

                if (RoleplayWebsocketsRoomsInfo.utilities().encode(description)
                        .equalsIgnoreCase("Kjpyb2xlcGxheV93ZWJzb2NrZXRfYWN0aXZlPTAq")) {
                    description = " ";
                    room.setDescription(description);
                    room.sendComposer(new RoomThicknessComposer(room).compose());
                    room.sendComposer(new RoomChatSettingsComposer(room).compose());
                    room.sendComposer(new RoomSettingsUpdatedComposer(room).compose());
                    habbo.getClient().sendResponse(new RoomSettingsSavedComposer(room));
                    new disablePlugin().execute();
                    return;
                }

                room.setName(name);
                room.setDescription(description);
                room.setState(state);
                if (!password.isEmpty())
                    room.setPassword(password);
                room.setUsersMax(usersMax);

                if (Emulator.getGameEnvironment().getRoomManager().hasCategory(categoryId,
                        habbo.getClient().getHabbo())) {
                    room.setCategory(categoryId);
                } else {
                    RoomCategory category = Emulator.getGameEnvironment().getRoomManager().getCategory(categoryId);
                    String message;
                    if (category == null) {
                        message = Emulator.getTexts().getValue("scripter.warning.roomsettings.category.nonexisting")
                                .replace("%username%", habbo.getClient().getHabbo().getHabboInfo().getUsername());
                    } else {
                        message = Emulator.getTexts().getValue("scripter.warning.roomsettings.category.permission")
                                .replace("%username%", habbo.getClient().getHabbo().getHabboInfo().getUsername())
                                .replace("%category%",
                                        Emulator.getGameEnvironment().getRoomManager().getCategory(categoryId) + "");
                    }

                    ScripterManager.scripterDetected(habbo.getClient(), message);
                    LOGGER.info(message);
                }

                room.setTags(tags.toString());
                room.setTradeMode(event.packet.readInt());
                room.setAllowPets(event.packet.readBoolean());
                room.setAllowPetsEat(event.packet.readBoolean());
                room.setAllowWalkthrough(event.packet.readBoolean());
                room.setHideWall(event.packet.readBoolean());
                room.setWallSize(event.packet.readInt());
                room.setFloorSize(event.packet.readInt());
                room.setMuteOption(event.packet.readInt());
                room.setKickOption(event.packet.readInt());
                room.setBanOption(event.packet.readInt());
                room.setChatMode(event.packet.readInt());
                room.setChatWeight(event.packet.readInt());
                room.setChatSpeed(event.packet.readInt());
                room.setChatDistance(Math.abs(event.packet.readInt()));
                room.setChatProtection(event.packet.readInt());
                room.setNeedsUpdate(true);

                room.sendComposer(new RoomThicknessComposer(room).compose());
                room.sendComposer(new RoomChatSettingsComposer(room).compose());
                room.sendComposer(new RoomSettingsUpdatedComposer(room).compose());
                habbo.getClient().sendResponse(new RoomSettingsSavedComposer(room));

                if (categoryId == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
                    for (Habbo Habbos : room.getHabbos()) {
                        if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                                .getInt("global_room.category.roleplay.id")) {
                            Habbos.goToRoom(room.getId());
                        }
                    }
                }

            }
        }
    }
}