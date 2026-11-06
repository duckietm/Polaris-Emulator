package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomSettingsSavedComposer;
import polaris.messages.outgoing.rooms.RoomSettingsUpdatedComposer;

/**
 * UpdateRoomCategoryAndTradeSettings (1265): the answer of the enforce
 * category dialog opened by RoomCategorySelectionEnforcement (3896). Only the
 * owner may move the room, only into a category they can use, and only with
 * one of the three trade modes of the room settings.
 */
public class UpdateRoomCategoryAndTradeSettingsEvent extends MessageHandler {
    private static final int MAX_TRADE_MODE = 2;

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        int categoryId = this.packet.readInt();
        int tradeMode = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room == null || !room.isOwner(this.client.getUser())) {
            return;
        }

        if (tradeMode < 0 || tradeMode > MAX_TRADE_MODE) {
            return;
        }

        if (!Emulator.getGameEnvironment().getRoomManager().hasCategory(categoryId, this.client.getUser())) {
            return;
        }

        room.setCategory(categoryId);
        room.setTradeMode(tradeMode);
        room.setNeedsUpdate(true);

        room.sendComposer(new RoomSettingsUpdatedComposer(room).compose());
        this.client.sendResponse(new RoomSettingsSavedComposer(room));
    }
}
