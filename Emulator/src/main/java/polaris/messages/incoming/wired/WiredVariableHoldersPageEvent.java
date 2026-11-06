package polaris.messages.incoming.wired;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.RoomWiredVariableCatalog;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredVariableHoldersPageComposer;

/**
 * Official AIR 13 variable overview page (975): one page of the holders of a wired variable with the
 * user-type and sort filters, answered by {@code WiredUserVariablesPage}.
 */
public class WiredVariableHoldersPageEvent extends MessageHandler {
    private static final int MAX_PAGE_SIZE = 200;

    @Override
    public void handle() throws Exception {
        String variableId = this.packet.readString();
        int page = this.packet.readInt();
        int pageSize = this.packet.readInt();
        int userTypeFilter = this.packet.readInt();
        int sortTypeFilter = this.packet.readInt();
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getUser())) {
            return;
        }

        int safePageSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(1, page);

        List<RoomWiredVariableCatalog.Holder> holders = RoomWiredVariableCatalog.filterAndSort(
                room, RoomWiredVariableCatalog.holders(room, variableId), userTypeFilter, sortTypeFilter);

        int totalEntries = holders.size();
        int firstIndex = (int) Math.min((long) (safePage - 1) * safePageSize, totalEntries);
        int lastIndex = Math.min(firstIndex + safePageSize, totalEntries);

        this.client.sendResponse(new WiredVariableHoldersPageComposer(
                variableId,
                totalEntries,
                safePage,
                safePageSize,
                new ArrayList<>(holders.subList(firstIndex, lastIndex)),
                userTypeFilter,
                sortTypeFilter));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
