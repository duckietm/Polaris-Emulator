package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.items.lovelock.LoveLockFurniFinishedComposer;
import polaris.messages.outgoing.rooms.items.lovelock.LoveLockFurniStartComposer;

public class InteractionLoveLock extends UserItem {
    public int userOneId;
    public int userTwoId;
    public boolean userOneConfirmed;
    public boolean userTwoConfirmed;

    public InteractionLoveLock(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionLoveLock(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(2 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(6);

        String[] data = this.getExtradata().split("\t");

        if (data.length == 6) {
            serverMessage.appendString("1");
            serverMessage.appendString(data[1]);
            serverMessage.appendString(data[2]);
            serverMessage.appendString(data[3]);
            serverMessage.appendString(data[4]);
            serverMessage.appendString(data[5]);
        } else {
            serverMessage.appendString("0");
            serverMessage.appendString("");
            serverMessage.appendString("");
            serverMessage.appendString("");
            serverMessage.appendString("");
            serverMessage.appendString("");
        }

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (this.getExtradata().contains("\t")) return;

        if (client == null) return;

        if (!RoomLayout.tilesAdjecent(
                client.getUser().getRoomUnit().getCurrentLocation(),
                room.getLayout().getTile(this.getX(), this.getY()))) return;

        int userId = client.getUser().getUserInfo().getId();

        if (this.userOneId == 0) {
            this.userOneId = userId;
            client.sendResponse(new LoveLockFurniStartComposer(this, true));
            return;
        }

        if (this.userOneId == userId) return;

        if (this.userTwoId == 0) {
            User first = room.getUser(this.userOneId);

            if (first != null) {
                this.userTwoId = userId;
                client.sendResponse(new LoveLockFurniStartComposer(this, false));
            }
        }
    }

    public void cancel(User user) {
        int partnerId = 0;

        if (this.userOneId == user.getUserInfo().getId()) {
            partnerId = this.userTwoId;
        } else if (this.userTwoId == user.getUserInfo().getId()) {
            partnerId = this.userOneId;
        }

        this.resetSession();

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null || partnerId <= 0) return;

        User partner = room.getUser(partnerId);
        if (partner != null && partner.getClient() != null) {
            partner.getClient().sendResponse(new LoveLockFurniFinishedComposer(this));
        }
    }

    public void resetSession() {
        this.userOneId = 0;
        this.userTwoId = 0;
        this.userOneConfirmed = false;
        this.userTwoConfirmed = false;
    }

    public boolean lock(User userOne, User userTwo, Room room) {
        RoomTile tile = room.getLayout().getTile(this.getX(), this.getY());
        if (RoomLayout.tilesAdjecent(userOne.getRoomUnit().getCurrentLocation(), tile)
                && RoomLayout.tilesAdjecent(userTwo.getRoomUnit().getCurrentLocation(), tile)) {
            String data = "1";
            data += "\t";
            data += userOne.getUserInfo().getUsername();
            data += "\t";
            data += userTwo.getUserInfo().getUsername();
            data += "\t";
            data += userOne.getUserInfo().getLook();
            data += "\t";
            data += userTwo.getUserInfo().getLook();
            data += "\t";
            data += Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "-"
                    + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "-"
                    + Calendar.getInstance().get(Calendar.YEAR);

            this.setExtradata(data);
            this.needsUpdate(true);
            Emulator.getThreading().run(this);
            room.updateItem(this);

            return true;
        }

        return false;
    }
}
