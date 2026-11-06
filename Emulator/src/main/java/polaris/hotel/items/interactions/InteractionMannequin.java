package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UserDataComposer;

public class InteractionMannequin extends UserItem {
    public InteractionMannequin(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionMannequin(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public int getMaximumRotations() {
        return 8;
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(1 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(3);
        if (this.getExtradata().split(":").length >= 2) {
            String[] data = this.getExtradata().split(":");
            serverMessage.appendString("GENDER");
            serverMessage.appendString(data[0].toLowerCase());
            serverMessage.appendString("FIGURE");
            serverMessage.appendString(data[1]);
            serverMessage.appendString("OUTFIT_NAME");
            serverMessage.appendString((data.length >= 3 ? data[2] : ""));
        } else {
            serverMessage.appendString("GENDER");
            serverMessage.appendString("m");
            serverMessage.appendString("FIGURE");
            serverMessage.appendString("");
            serverMessage.appendString("OUTFIT_NAME");
            serverMessage.appendString("My Look");
            this.setExtradata("m: :My look");
            this.needsUpdate(true);
            Emulator.getThreading().run(this);
        }
        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        String[] data = this.getExtradata().split(":");

        if (data.length < 2) return;

        String gender = data[0];
        String figure = data[1];

        if (gender.isEmpty()
                || figure.isEmpty()
                || (!gender.equalsIgnoreCase("m") && !gender.equalsIgnoreCase("f"))
                || !client.getUser().getUserInfo().getGender().name().equalsIgnoreCase(gender)) return;

        String newFigure = "";

        for (String playerFigurePart : client.getUser().getUserInfo().getLook().split("\\.")) {
            if (!playerFigurePart.startsWith("ch") && !playerFigurePart.startsWith("lg"))
                newFigure += playerFigurePart + ".";
        }

        String newFigureParts = figure;

        for (String newFigurePart : newFigureParts.split("\\.")) {
            if (newFigurePart.startsWith("hd")) newFigureParts = newFigureParts.replace(newFigurePart, "");
        }

        if (newFigureParts.equals("")) return;

        String newLook = newFigure + newFigureParts;

        if (newLook.length() > 512) return;

        client.getUser()
                .getUserInfo()
                .setLook(
                        ClothingValidationManager.VALIDATE_ON_MANNEQUIN
                                ? ClothingValidationManager.validateLook(
                                        client.getUser(),
                                        newLook,
                                        client.getUser()
                                                .getUserInfo()
                                                .getGender()
                                                .name())
                                : newLook);
        room.sendComposer(new RoomUserDataComposer(client.getUser()).compose());
        client.sendResponse(new UserDataComposer(client.getUser()));
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}
}
