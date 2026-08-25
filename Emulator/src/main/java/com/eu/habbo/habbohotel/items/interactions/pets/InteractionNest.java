package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionNest extends HabboItem {
    public InteractionNest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionNest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return this.getBaseItem().allowWalk();
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        if (pet == null)
            return;

        if (pet instanceof RideablePet && ((RideablePet) pet).getRider() != null)
            return;

        if (!pet.getPetData().haveNest(this))
            return;

        if (pet.getEnergy() > 85)
            return;

        pet.setTask(PetTasks.NEST);
        pet.getRoomUnit().setGoalLocation(room.getLayout().getTile(this.getX(), this.getY()));
        pet.getRoomUnit().clearStatus();
        pet.getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        pet.getRoomUnit().setStatus(RoomUnitStatus.LAY, room.getStackHeight(this.getX(), this.getY(), false) + "");
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
    }
}
