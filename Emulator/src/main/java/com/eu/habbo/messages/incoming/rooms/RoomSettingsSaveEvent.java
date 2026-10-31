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

    /** The name, description, door, capacity, category and tags the packet starts with, every check passed. */
    private record RoomBasics(
            String name,
            String description,
            RoomState state,
            String password,
            int usersMax,
            int categoryId,
            StringBuilder tags) {}

    /** The trade, pet, wall, moderation and chat options, every one in range. */
    private record RoomOptions(
            int tradeMode,
            boolean allowPets,
            boolean allowPetsEat,
            boolean allowWalkthrough,
            boolean hideWall,
            int wallSize,
            int floorSize,
            int muteOption,
            int kickOption,
            int banOption,
            int chatMode,
            int chatWeight,
            int chatSpeed,
            int chatDistance,
            int chatProtection) {}

    /** The pet, door tile and idle settings newer clients send last, the timeouts in range. */
    private record IdleSettings(
            boolean muteAllPets,
            boolean leaveOnDoorTileEnabled,
            boolean sleepEnabled,
            int sleepTimeoutSeconds,
            boolean autokickEnabled,
            int autokickTimeoutSeconds) {}

    /** The fields newer clients add at the end; null where the client left them out. */
    private record RoomExtras(Boolean allowUnderpass, IdleSettings idle) {}

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room == null || !room.isOwner(this.client.getHabbo())) return;

        // The whole packet is read and checked before the room is touched: a refused field used to leave the
        // fields before it written into the room, unsaved and unannounced.
        RoomBasics basics = this.readBasics(room);

        if (basics == null) return;

        RoomOptions options = this.readOptions();

        if (options == null) return;

        RoomExtras extras = this.readExtras();

        if (extras == null) return;

        boolean enforceCategory = this.applyBasics(room, basics);
        this.applyOptions(room, basics.tags(), options);
        this.applyExtras(room, extras);

        this.announce(room, enforceCategory);
    }

    /** Reads and checks the fields up to the tags; null once the packet is refused, with an answer where it has one. */
    private RoomBasics readBasics(Room room) {
        String name = this.packet.readString();

        if (name.trim().isEmpty() || name.length() > 60) {
            this.client.sendResponse(new RoomEditSettingsErrorComposer(
                    room.getId(), RoomEditSettingsErrorComposer.ROOM_NAME_MISSING, ""));
            return null;
        }

        if (!Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(name, this.client.getHabbo())
                .equals(name)) {
            this.client.sendResponse(new RoomEditSettingsErrorComposer(
                    room.getId(), RoomEditSettingsErrorComposer.ROOM_NAME_BADWORDS, ""));
            return null;
        }

        String description = this.packet.readString();

        if (description.length() > 255) {
            return null;
        }

        if (!Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(description, this.client.getHabbo())
                .equals(description)) {
            this.client.sendResponse(new RoomEditSettingsErrorComposer(
                    room.getId(), RoomEditSettingsErrorComposer.ROOM_DESCRIPTION_BADWORDS, ""));
            return null;
        }

        int stateId = this.packet.readInt();
        if (stateId < 0 || stateId >= RoomState.values().length) {
            return null;
        }
        RoomState state = RoomState.values()[stateId];

        String password = this.packet.readString();
        if (password == null || password.length() > MAX_ROOM_PASSWORD_LENGTH) {
            return null;
        }
        if (state == RoomState.PASSWORD
                && password.isEmpty()
                && (room.getPassword() == null || room.getPassword().isEmpty())) {
            this.client.sendResponse(new RoomEditSettingsErrorComposer(
                    room.getId(), RoomEditSettingsErrorComposer.PASSWORD_REQUIRED, ""));
            return null;
        }

        int usersMax = this.packet.readInt();
        if (!isInRange(usersMax, MIN_USERS_MAX, MAX_USERS_MAX)) {
            return null;
        }

        int categoryId = this.packet.readInt();
        StringBuilder tags = this.readTags(room);

        if (tags == null) return null;

        return new RoomBasics(name, description, state, password, usersMax, categoryId, tags);
    }

    /** The tags, each once, joined by ";"; null once a tag is refused. */
    private StringBuilder readTags(Room room) {
        StringBuilder tags = new StringBuilder();
        Set<String> uniqueTags = new LinkedHashSet<>();
        int count = this.packet.readInt();
        if (count < 0 || count > MAX_TAGS) {
            return null;
        }
        for (int i = 0; i < count; i++) {
            String tag = this.packet.readString();

            // The client marks the tag field whose text equals the info string (AIR RoomSettingsCtrl).
            if (tag.length() > 15) {
                this.client.sendResponse(new RoomEditSettingsErrorComposer(
                        room.getId(), RoomEditSettingsErrorComposer.TAGS_TOO_LONG, tag));
                return null;
            }
            // The tags are stored joined by ";": a tag holding one became several, past both the count and the
            // length limits.
            if (!isAcceptableTag(tag)) {
                this.client.sendResponse(new RoomEditSettingsErrorComposer(
                        room.getId(), RoomEditSettingsErrorComposer.RESTRICTED_TAGS, tag));
                return null;
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
                    tag -> !wordFilter.filter(tag, this.client.getHabbo()).equals(tag));
            this.client.sendResponse(new RoomEditSettingsErrorComposer(
                    room.getId(), RoomEditSettingsErrorComposer.ROOM_TAGS_BADWWORDS, badTag));
            return null;
        }

        if (tags.length() > 0) {
            for (String entry :
                    Emulator.getConfig().getValue("hotel.room.tags.staff").split(";")) {
                // An empty entry (an empty setting, or "a;;b") is contained in every text and refused every tag.
                String staffTag = entry.trim();
                if (staffTag.isEmpty()) continue;

                if (tags.toString().contains(staffTag)) {
                    this.client.sendResponse(new RoomEditSettingsErrorComposer(
                            room.getId(),
                            RoomEditSettingsErrorComposer.RESTRICTED_TAGS,
                            offendingTag(uniqueTags, tag -> tag.contains(staffTag))));
                    return null;
                }
            }
        }

        return tags;
    }

    /** Writes the basics into the room; true when the client has to be asked for another category afterwards. */
    private boolean applyBasics(Room room, RoomBasics basics) {
        room.setName(basics.name());
        room.setDescription(basics.description());
        room.setState(basics.state());
        if (!basics.password().isEmpty()) room.setPassword(basics.password());
        room.setUsersMax(basics.usersMax());

        int categoryId = basics.categoryId();

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
                                Emulator.getGameEnvironment().getRoomManager().getCategory(categoryId) + "");
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
            }
        }
        return enforceCategory;
    }

    /** Reads the trade, pet, wall, moderation and chat options; null when one is out of range. */
    private RoomOptions readOptions() {
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
            return null;
        }

        return new RoomOptions(
                tradeMode,
                allowPets,
                allowPetsEat,
                allowWalkthrough,
                hideWall,
                wallSize,
                floorSize,
                muteOption,
                kickOption,
                banOption,
                chatMode,
                chatWeight,
                chatSpeed,
                chatDistance,
                chatProtection);
    }

    private void applyOptions(Room room, StringBuilder tags, RoomOptions options) {
        room.setTags(tags.toString());
        room.setTradeMode(options.tradeMode());
        room.setAllowPets(options.allowPets());
        room.setAllowPetsEat(options.allowPetsEat());
        room.setAllowWalkthrough(options.allowWalkthrough());
        room.setHideWall(options.hideWall());
        room.setWallSize(options.wallSize());
        room.setFloorSize(options.floorSize());
        room.setMuteOption(options.muteOption());
        room.setKickOption(options.kickOption());
        room.setBanOption(options.banOption());
        room.setChatMode(options.chatMode());
        room.setChatWeight(options.chatWeight());
        room.setChatSpeed(options.chatSpeed());
        room.setChatDistance(options.chatDistance());
        room.setChatProtection(options.chatProtection());
    }

    /**
     * The fields newer clients add at the end: underpass, then the pet, door tile and idle settings. Older clients
     * leave them out. Null when an idle timeout is out of range.
     */
    private RoomExtras readExtras() {
        Boolean allowUnderpass = this.packet.bytesAvailable() > 0 ? this.packet.readBoolean() : null;

        if (this.packet.bytesAvailable() <= 0) return new RoomExtras(allowUnderpass, null);

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
            return null;
        }

        return new RoomExtras(
                allowUnderpass,
                new IdleSettings(
                        muteAllPets,
                        leaveOnDoorTileEnabled,
                        idleSleepEnabled,
                        idleSleepTimeoutSeconds,
                        idleAutokickEnabled,
                        idleAutokickTimeoutSeconds));
    }

    private void applyExtras(Room room, RoomExtras extras) {
        if (extras.allowUnderpass() != null) room.setAllowUnderpass(extras.allowUnderpass());

        IdleSettings idle = extras.idle();

        if (idle == null) return;

        room.setMuteAllPets(idle.muteAllPets());
        room.setLeaveOnDoorTileEnabled(idle.leaveOnDoorTileEnabled());
        room.setIdleSleepEnabled(idle.sleepEnabled());
        room.setIdleSleepTimeoutSeconds(idle.sleepEnabled() ? idle.sleepTimeoutSeconds() : 0);
        room.setIdleAutokickEnabled(idle.autokickEnabled());
        room.setIdleAutokickTimeoutSeconds(idle.autokickEnabled() ? idle.autokickTimeoutSeconds() : 0);
    }

    /** Marks the room for saving and tells everyone in it and the owner about the new settings. */
    private void announce(Room room, boolean enforceCategory) {
        room.setNeedsUpdate(true);

        room.sendComposer(new RoomThicknessComposer(room).compose());
        room.sendComposer(new RoomChatSettingsComposer(room).compose());
        room.sendComposer(new RoomSettingsUpdatedComposer(room).compose());
        this.client.sendResponse(new RoomSettingsSavedComposer(room));
        if (enforceCategory) {
            this.client.sendResponse(
                    new RoomCategoryUpdateMessageComposer(RoomCategoryUpdateMessageComposer.SELECTION_ROOM_SETTINGS));
        }
        // TODO Find packet for update room name.
    }

    /** First tag the predicate rejects, or "" when none does on its own. */
    /** A tag without the ";" that separates the stored tags, and without control characters. */
    static boolean isAcceptableTag(String tag) {
        if (tag == null) return false;

        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (c == ';' || Character.isISOControl(c)) return false;
        }

        return true;
    }

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
