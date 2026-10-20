package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.habbohotel.rooms.RoomState;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomChatSettingsComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomEditSettingsErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomSettingsSavedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomSettingsUpdatedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomThicknessComposer;
import com.eu.habbo.messages.outgoing.unknown.RoomCategoryUpdateMessageComposer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomSettingsSaveEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomSettingsSaveEvent.class);
    private static final int MAX_TAGS = 2;
    private static final int MAX_ROOM_PASSWORD_LENGTH = 64;
    private static final int MIN_USERS_MAX = 1;
    private static final int MAX_USERS_MAX = 200;
    private static final int MIN_WALL_OR_FLOOR_SIZE = -2;
    private static final int MAX_WALL_OR_FLOOR_SIZE = 1;
    private static final int MAX_OPTION_LEVEL = 2;
    private static final int MIN_CHAT_DISTANCE = 1;
    private static final int MAX_CHAT_DISTANCE = 99;

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room != null) {
            if (room.isOwner(this.client.getHabbo())) {
                String name = this.packet.readString();

                if (name.trim().isEmpty() || name.length() > 60) {
                    this.client.sendResponse(new RoomEditSettingsErrorComposer(
                            room.getId(), RoomEditSettingsErrorComposer.ROOM_NAME_MISSING, ""));
                    return;
                }

                if (!Emulator.getGameEnvironment()
                        .getWordFilter()
                        .filter(name, this.client.getHabbo())
                        .equals(name)) {
                    this.client.sendResponse(new RoomEditSettingsErrorComposer(
                            room.getId(), RoomEditSettingsErrorComposer.ROOM_NAME_BADWORDS, ""));
                    return;
                }

                String description = this.packet.readString();

                if (description.length() > 255) {
                    return;
                }

                if (!Emulator.getGameEnvironment()
                        .getWordFilter()
                        .filter(description, this.client.getHabbo())
                        .equals(description)) {
                    this.client.sendResponse(new RoomEditSettingsErrorComposer(
                            room.getId(), RoomEditSettingsErrorComposer.ROOM_DESCRIPTION_BADWORDS, ""));
                    return;
                }

                int stateId = this.packet.readInt();
                if (stateId < 0 || stateId >= RoomState.values().length) {
                    return;
                }
                RoomState state = RoomState.values()[stateId];

                String password = this.packet.readString();
                if (password == null || password.length() > MAX_ROOM_PASSWORD_LENGTH) {
                    return;
                }
                if (state == RoomState.PASSWORD
                        && password.isEmpty()
                        && (room.getPassword() == null || room.getPassword().isEmpty())) {
                    this.client.sendResponse(new RoomEditSettingsErrorComposer(
                            room.getId(), RoomEditSettingsErrorComposer.PASSWORD_REQUIRED, ""));
                    return;
                }

                int usersMax = this.packet.readInt();
                if (!isInRange(usersMax, MIN_USERS_MAX, MAX_USERS_MAX)) {
                    return;
                }

                int categoryId = this.packet.readInt();
                StringBuilder tags = new StringBuilder();
                Set<String> uniqueTags = new LinkedHashSet<>();
                int count = this.packet.readInt();
                if (count < 0 || count > MAX_TAGS) {
                    return;
                }
                for (int i = 0; i < count; i++) {
                    String tag = this.packet.readString();

                    // The client marks the tag field whose text equals the info string (AIR RoomSettingsCtrl).
                    if (tag.length() > 15) {
                        this.client.sendResponse(new RoomEditSettingsErrorComposer(
                                room.getId(), RoomEditSettingsErrorComposer.TAGS_TOO_LONG, tag));
                        return;
                    }
                    if (!uniqueTags.contains(tag)) {
                        uniqueTags.add(tag);
                        tags.append(tag).append(";");
                    }
                }

                WordFilter wordFilter = Emulator.getGameEnvironment().getWordFilter();
                if (!wordFilter.filter(tags.toString(), this.client.getHabbo()).contentEquals(tags)) {
                    String badTag = offendingTag(
                            uniqueTags,
                            tag -> !wordFilter
                                    .filter(tag, this.client.getHabbo())
                                    .equals(tag));
                    this.client.sendResponse(new RoomEditSettingsErrorComposer(
                            room.getId(), RoomEditSettingsErrorComposer.ROOM_TAGS_BADWWORDS, badTag));
                    return;
                }

                if (tags.length() > 0) {
                    for (String s : Emulator.getConfig()
                            .getValue("hotel.room.tags.staff")
                            .split(";")) {
                        if (tags.toString().contains(s)) {
                            this.client.sendResponse(new RoomEditSettingsErrorComposer(
                                    room.getId(),
                                    RoomEditSettingsErrorComposer.RESTRICTED_TAGS,
                                    offendingTag(uniqueTags, tag -> tag.contains(s))));
                            return;
                        }
                    }
                }

                room.setName(name);
                room.setDescription(description);
                room.setState(state);
                if (!password.isEmpty()) room.setPassword(password);
                room.setUsersMax(usersMax);

                // Official flow: a room saved without a usable category keeps
                // its old one and the client is told to pick a valid category
                // (RoomCategoryUpdateMessage 3896 -> enforce category dialog).
                boolean enforceCategory = false;
                if (Emulator.getGameEnvironment().getRoomManager().hasCategory(categoryId, this.client.getHabbo()))
                    room.setCategory(categoryId);
                else {
                    RoomCategory category =
                            Emulator.getGameEnvironment().getRoomManager().getCategory(categoryId);
                    enforceCategory = true;

                    if (category == null) {
                        LOGGER.info(
                                "Room {} saved without a valid category ({}) by {}; enforcing a category choice",
                                room.getId(),
                                categoryId,
                                this.client.getHabbo().getHabboInfo().getUsername());
                    } else {
                        String message = Emulator.getTexts()
                                .getValue("scripter.warning.roomsettings.category.permission")
                                .replace(
                                        "%username%",
                                        this.client.getHabbo().getHabboInfo().getUsername())
                                .replace(
                                        "%category%",
                                        Emulator.getGameEnvironment()
                                                        .getRoomManager()
                                                        .getCategory(categoryId) + "");
                        ScripterManager.scripterDetected(this.client, message);
                        LOGGER.info(message);
                    }
                }

                int tradeMode = this.packet.readInt();
                boolean allowPets = this.packet.readBoolean();
                boolean allowPetsEat = this.packet.readBoolean();
                boolean allowWalkthrough = this.packet.readBoolean();
                boolean hideWall = this.packet.readBoolean();
                int wallSize = this.packet.readInt();
                int floorSize = this.packet.readInt();
                int muteOption = this.packet.readInt();
                int kickOption = this.packet.readInt();
                int banOption = this.packet.readInt();
                int chatMode = this.packet.readInt();
                int chatWeight = this.packet.readInt();
                int chatSpeed = this.packet.readInt();
                int chatDistance = this.packet.readInt();
                int chatProtection = this.packet.readInt();

                if (!isInRange(tradeMode, 0, MAX_OPTION_LEVEL)
                        || !isInRange(wallSize, MIN_WALL_OR_FLOOR_SIZE, MAX_WALL_OR_FLOOR_SIZE)
                        || !isInRange(floorSize, MIN_WALL_OR_FLOOR_SIZE, MAX_WALL_OR_FLOOR_SIZE)
                        || !isInRange(muteOption, 0, MAX_OPTION_LEVEL)
                        || !isInRange(kickOption, 0, MAX_OPTION_LEVEL)
                        || !isInRange(banOption, 0, MAX_OPTION_LEVEL)
                        || !isInRange(chatMode, 0, MAX_OPTION_LEVEL)
                        || !isInRange(chatWeight, 0, MAX_OPTION_LEVEL)
                        || !isInRange(chatSpeed, 0, MAX_OPTION_LEVEL)
                        || !isInRange(chatDistance, MIN_CHAT_DISTANCE, MAX_CHAT_DISTANCE)
                        || !isInRange(chatProtection, 0, MAX_OPTION_LEVEL)) {
                    return;
                }

                room.setTags(tags.toString());
                room.setTradeMode(tradeMode);
                room.setAllowPets(allowPets);
                room.setAllowPetsEat(allowPetsEat);
                room.setAllowWalkthrough(allowWalkthrough);
                room.setHideWall(hideWall);
                room.setWallSize(wallSize);
                room.setFloorSize(floorSize);
                room.setMuteOption(muteOption);
                room.setKickOption(kickOption);
                room.setBanOption(banOption);
                room.setChatMode(chatMode);
                room.setChatWeight(chatWeight);
                room.setChatSpeed(chatSpeed);
                room.setChatDistance(chatDistance);
                room.setChatProtection(chatProtection);

                if (this.packet.bytesAvailable() > 0) {
                    room.setAllowUnderpass(this.packet.readBoolean());
                }

                if (this.packet.bytesAvailable() > 0) {
                    boolean muteAllPets = this.packet.readBoolean();
                    boolean leaveOnDoorTileEnabled = this.packet.readBoolean();
                    boolean idleSleepEnabled = this.packet.readBoolean();
                    int idleSleepTimeoutSeconds = this.packet.readInt();
                    boolean idleAutokickEnabled = this.packet.readBoolean();
                    int idleAutokickTimeoutSeconds = this.packet.readInt();

                    if ((idleSleepEnabled && !isInRange(idleSleepTimeoutSeconds, 30, 3600))
                            || (idleAutokickEnabled && !isInRange(idleAutokickTimeoutSeconds, 60, 36000))
                            || (idleSleepEnabled
                                    && idleAutokickEnabled
                                    && idleAutokickTimeoutSeconds < idleSleepTimeoutSeconds + 30)) {
                        return;
                    }

                    room.setMuteAllPets(muteAllPets);
                    room.setLeaveOnDoorTileEnabled(leaveOnDoorTileEnabled);
                    room.setIdleSleepEnabled(idleSleepEnabled);
                    room.setIdleSleepTimeoutSeconds(idleSleepEnabled ? idleSleepTimeoutSeconds : 0);
                    room.setIdleAutokickEnabled(idleAutokickEnabled);
                    room.setIdleAutokickTimeoutSeconds(idleAutokickEnabled ? idleAutokickTimeoutSeconds : 0);
                }

                room.setNeedsUpdate(true);

                room.sendComposer(new RoomThicknessComposer(room).compose());
                room.sendComposer(new RoomChatSettingsComposer(room).compose());
                room.sendComposer(new RoomSettingsUpdatedComposer(room).compose());
                this.client.sendResponse(new RoomSettingsSavedComposer(room));
                if (enforceCategory) {
                    this.client.sendResponse(new RoomCategoryUpdateMessageComposer(
                            RoomCategoryUpdateMessageComposer.SELECTION_ROOM_SETTINGS));
                }
                // TODO Find packet for update room name.
            }
        }
    }

    /** First tag the predicate rejects, or "" when none does on its own. */
    static String offendingTag(Iterable<String> tags, Predicate<String> offends) {
        for (String tag : tags) {
            if (offends.test(tag)) {
                return tag;
            }
        }
        return "";
    }

    private static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }
}
