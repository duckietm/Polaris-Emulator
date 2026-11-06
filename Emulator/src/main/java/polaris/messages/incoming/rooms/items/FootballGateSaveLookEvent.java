package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.games.football.InteractionFootballGate;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class FootballGateSaveLookEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        // AIR FurnitureClothingChangeWidgetHandler offers the editor to the room owner, any-room controllers and
        // anyone with rights in the room (controller level 1+), which is what Room.hasRights covers.
        if (room == null || !room.hasRights(this.client.getUser())) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        UserItem item = room.getUserItem(itemId);
        if (!(item instanceof InteractionFootballGate)) return;

        String gender = this.packet.readString();
        String look = this.packet.readString();

        if (!RoomItemInputGuard.isValidGender(gender) || !RoomItemInputGuard.isValidFigure(look)) return;

        switch (gender.toLowerCase()) {
            case "m":
                ((InteractionFootballGate) item).setFigureM(look);
                room.updateItem(item);
                break;

            case "f":
                ((InteractionFootballGate) item).setFigureF(look);
                room.updateItem(item);
                break;
        }
    }
}
