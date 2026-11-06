package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.wired.WiredExtraDataComposer;

public abstract class InteractionWiredExtra extends InteractionWired {
    protected InteractionWiredExtra(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredExtra(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (room.canInspectWired(client.getUser())) {
                if (this.hasConfiguration()) {
                    client.sendResponse(new WiredExtraDataComposer(this, room, client.getUser()));
                }
                this.activateBox(room);
            }
        }
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        return true;
    }

    public boolean hasConfiguration() {
        return false;
    }

    /** Settings as the given viewer may see them; boxes holding secrets override this. */
    public void serializeWiredDataFor(ServerMessage message, Room room, User viewer) {
        this.serializeWiredData(message, room);
    }
}
