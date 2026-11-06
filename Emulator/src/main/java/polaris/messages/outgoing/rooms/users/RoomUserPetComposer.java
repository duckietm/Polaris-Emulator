package polaris.messages.outgoing.rooms.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserPetComposer extends MessageComposer {
    private final int petType;
    private final int race;
    private final String color;
    private final User user;

    public RoomUserPetComposer(int petType, int race, String color, User user) {
        this.petType = petType;
        this.race = race;
        this.color = color;
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUsersComposer);
        this.response.appendInt(1);
        this.response.appendInt(this.user.getUserInfo().getId());
        this.response.appendString(this.user.getUserInfo().getUsername());
        this.response.appendString("");
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendString(this.petType + " " + this.race + " " + this.color + " 2 2 -1 0 3 -1 0");
        this.response.appendInt(this.user.getRoomUnit().getId());
        this.response.appendInt(this.user.getRoomUnit().getX());
        this.response.appendInt(this.user.getRoomUnit().getY());
        this.response.appendString(this.user.getRoomUnit().getZ() + "");
        this.response.appendInt(this.user.getRoomUnit().getBodyRotation().getValue());
        this.response.appendInt(2);
        this.response.appendInt(this.petType);
        this.response.appendInt(this.user.getUserInfo().getId());
        this.response.appendString(this.user.getUserInfo().getUsername());
        this.response.appendInt(1);
        this.response.appendBoolean(false);
        this.response.appendBoolean(true);
        this.response.appendBoolean(true); // Can toggle breeding permissions.
        this.response.appendBoolean(true);
        this.response.appendBoolean(true); // Can treat?
        this.response.appendBoolean(true); // Can breed
        this.response.appendInt(0);
        this.response.appendString("");
        this.response.appendString("unknown");
        this.response.appendInt(0);
        this.response.appendInt(0);
        return this.response;
    }

    public int getPetType() {
        return petType;
    }

    public int getRace() {
        return race;
    }

    public String getColor() {
        return color;
    }

    public User getUser() {
        return user;
    }
}
