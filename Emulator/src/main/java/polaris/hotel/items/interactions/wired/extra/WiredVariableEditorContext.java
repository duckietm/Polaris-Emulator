package polaris.hotel.items.interactions.wired.extra;

import java.util.List;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.RoomWiredVariableCatalog;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.wired.WiredAllVariablesDiffComposer;

/**
 * The wired context a user or furni variable box's editor ends with (observed on AIR): type 2 (user) or 1 (furni),
 * the variable as the catalog lists it, then every holder in the room with its value, -1 for a valueless variable.
 */
final class WiredVariableEditorContext {
    static final int CONTEXT_FURNI_VARIABLE_INFO_AND_HOLDERS = 1;
    static final int CONTEXT_USER_VARIABLE_INFO_AND_HOLDERS = 2;

    private WiredVariableEditorContext() {}

    static void append(ServerMessage message, Room room, int catalogTarget, int itemId, int contextType) {
        if (room == null) return;
        String variableId = RoomWiredVariableCatalog.variableId(catalogTarget, itemId);
        RoomWiredVariableCatalog.Variable variable = RoomWiredVariableCatalog.byId(
                        RoomWiredVariableCatalog.variables(room))
                .get(variableId);
        if (variable == null) return;

        List<RoomWiredVariableCatalog.Holder> holders = RoomWiredVariableCatalog.holders(room, variableId, true);
        message.appendInt(1);
        message.appendInt(contextType);
        WiredAllVariablesDiffComposer.appendVariable(message, variable);
        message.appendInt(holders.size());
        for (RoomWiredVariableCatalog.Holder holder : holders) {
            message.appendInt(BuildersClubItemIds.toWire(
                    holder.getEntityId(), holder.getEntityType() == RoomWiredVariableCatalog.HOLDER_FURNI));
            message.appendInt(holder.hasValue() ? holder.getValue() : -1);
        }
    }
}
