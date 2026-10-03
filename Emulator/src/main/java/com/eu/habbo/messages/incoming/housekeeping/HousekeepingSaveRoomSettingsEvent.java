package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomSettingsUpdatedComposer;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the basic settings of any room from housekeeping: name, description,
 * maximum users, category, trade mode and tags. The room is written through
 * immediately, and anyone inside is told its settings changed.
 */
public class HousekeepingSaveRoomSettingsEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    private static final String ACTION_KEY = "room.settings";

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
        String name = HousekeepingInputGuard.normalize(this.packet.readString());
        String description = HousekeepingInputGuard.normalize(this.packet.readString());
        int usersMax = this.packet.readInt();
        int categoryId = this.packet.readInt();
        int tradeMode = this.packet.readInt();
        int tagCount = this.packet.readInt();

        if (!HousekeepingRoomSettingsInput.isValidTagCount(tagCount)) {
            this.fail("housekeeping.error.invalid_input");
            return;
        }

        List<String> tags = new ArrayList<>(tagCount);

        for (int i = 0; i < tagCount; i++) {
            tags.add(this.packet.readString());
        }

        String joinedTags = HousekeepingRoomSettingsInput.joinTags(tags);

        if (roomId <= 0
                || joinedTags == null
                || !HousekeepingRoomSettingsInput.isValidName(name)
                || !HousekeepingRoomSettingsInput.isValidDescription(description)
                || !HousekeepingRoomSettingsInput.isValidUsersMax(usersMax)
                || !HousekeepingRoomSettingsInput.isValidTradeMode(tradeMode)) {
            this.fail("housekeeping.error.invalid_input");
            return;
        }

        RoomManager roomManager = Emulator.getGameEnvironment().getRoomManager();

        if (roomManager.getCategory(categoryId) == null) {
            this.fail("housekeeping.error.invalid_category");
            return;
        }

        Room room = roomManager.loadRoom(roomId, false);

        if (room == null) {
            this.fail("housekeeping.error.room_not_found");
            return;
        }

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getHabbo(), room)) {
            this.fail("housekeeping.error.rank_too_high");
            return;
        }

        room.setName(name);
        room.setDescription(description);
        room.setUsersMax(usersMax);
        room.setCategory(categoryId);
        room.setTradeMode(tradeMode);
        room.setTags(joinedTags);
        room.setNeedsUpdate(true);
        room.save();

        room.sendComposer(new RoomSettingsUpdatedComposer(room).compose());

        HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                name,
                "roomId=" + roomId + " name=" + HousekeepingInputGuard.auditValue(name) + " usersMax=" + usersMax
                        + " category=" + categoryId + " tradeMode=" + tradeMode,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, roomId, ""));
    }

    private void fail(String message) {
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, message));
    }
}
