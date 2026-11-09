package polaris.messages.incoming.housekeeping;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import polaris.Emulator;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.messages.outgoing.rooms.RoomSettingsUpdatedComposer;

/**
 * Saves the basic settings of any room from housekeeping: name, description,
 * maximum users, category, trade mode and tags. The room is written through
 * immediately, and anyone inside is told its settings changed. The work is
 * {@link #save}, shared with the RCON housekeeping command.
 */
public class HousekeepingSaveRoomSettingsEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    public static final String ACTION_KEY = "room.settings";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int roomId = this.packet.readInt();
        String name = this.packet.readString();
        String description = this.packet.readString();
        int usersMax = this.packet.readInt();
        int categoryId = this.packet.readInt();
        int tradeMode = this.packet.readInt();
        int tagCount = this.packet.readInt();

        if (!HousekeepingRoomSettingsInput.isValidTagCount(tagCount)) {
            this.reply(ACTION_KEY, HousekeepingResult.fail("housekeeping.error.invalid_input"));
            return;
        }

        List<String> tags = new ArrayList<>(tagCount);

        for (int i = 0; i < tagCount; i++) {
            tags.add(this.packet.readString());
        }

        this.reply(
                ACTION_KEY,
                save(
                        this.operator(),
                        roomId,
                        name,
                        description,
                        usersMax,
                        categoryId,
                        tradeMode,
                        tags,
                        room -> HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)));
    }

    /**
     * Saves a room's settings. The panel sends every field; a null field (RCON may leave one out)
     * keeps the room's current value. The limits are {@link HousekeepingRoomSettingsInput}'s.
     *
     * @param mayManage the operator's rank guard on the room's owner
     */
    public static HousekeepingResult save(
            HousekeepingOperator operator,
            int roomId,
            String rawName,
            String rawDescription,
            Integer usersMaxValue,
            Integer categoryIdValue,
            Integer tradeModeValue,
            List<String> tagValues,
            Predicate<Room> mayManage) {
        String name = rawName == null ? null : HousekeepingInputGuard.normalize(rawName);
        String description = rawDescription == null ? null : HousekeepingInputGuard.normalize(rawDescription);
        String joinedTags = null;

        if (tagValues != null) {
            if (!HousekeepingRoomSettingsInput.isValidTagCount(tagValues.size())) {
                return HousekeepingResult.fail("housekeeping.error.invalid_input");
            }

            joinedTags = HousekeepingRoomSettingsInput.joinTags(tagValues);

            if (joinedTags == null) {
                return HousekeepingResult.fail("housekeeping.error.invalid_input");
            }
        }

        if (roomId <= 0
                || (name != null && !HousekeepingRoomSettingsInput.isValidName(name))
                || (description != null && !HousekeepingRoomSettingsInput.isValidDescription(description))
                || (usersMaxValue != null && !HousekeepingRoomSettingsInput.isValidUsersMax(usersMaxValue))
                || (tradeModeValue != null && !HousekeepingRoomSettingsInput.isValidTradeMode(tradeModeValue))) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        RoomManager roomManager = Emulator.getGameEnvironment().getRoomManager();

        if (categoryIdValue != null && roomManager.getCategory(categoryIdValue) == null) {
            return HousekeepingResult.fail("housekeeping.error.invalid_category");
        }

        Room room = roomManager.loadRoom(roomId, false);

        if (room == null) {
            return HousekeepingResult.fail("housekeeping.error.room_not_found");
        }

        if (!mayManage.test(room)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        if (name == null) name = room.getName();
        if (description == null) description = room.getDescription();
        int usersMax = usersMaxValue != null ? usersMaxValue : room.getUsersMax();
        int categoryId = categoryIdValue != null ? categoryIdValue : room.getCategory();
        int tradeMode = tradeModeValue != null ? tradeModeValue : room.getTradeMode();

        room.setName(name);
        room.setDescription(description);
        room.setUsersMax(usersMax);
        room.setCategory(categoryId);
        room.setTradeMode(tradeMode);
        if (joinedTags != null) room.setTags(joinedTags);
        room.setNeedsUpdate(true);
        room.save();

        room.sendComposer(new RoomSettingsUpdatedComposer(room).compose());

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                name,
                "roomId=" + roomId + " name=" + HousekeepingInputGuard.auditValue(name) + " usersMax=" + usersMax
                        + " category=" + categoryId + " tradeMode=" + tradeMode,
                operator.ip());
        return HousekeepingResult.ok(roomId);
    }
}
