package polaris.messages.incoming.rooms.pets;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomHanditemBlockSupport;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.threading.runnables.RoomUnitWalkToRoomUnit;
import polaris.threading.runnables.UserGiveHandItemToPet;

/**
 * The pet menu's "give hand item" entry: the sender walks up to the pet and hands over what they are
 * carrying, exactly like the entry that gives it to another user. The pet is addressed by its id, the
 * way the client's own pet infostand knows it.
 */
public class RoomUserGiveHandItemPetEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        User user = this.client.getUser();

        if (user == null || user.getRoomUnit() == null) return;

        Room room = this.currentRoom();

        if (room == null || RoomHanditemBlockSupport.isHanditemBlocked(room)) return;

        if (user.getRoomUnit().getHandItem() <= 0) return;

        Pet pet = room.getPet(petId);

        if (pet == null || pet.getRoomUnit() == null) return;

        List<Runnable> executable = new ArrayList<>();
        executable.add(new UserGiveHandItemToPet(user, pet));
        Emulator.getThreading()
                .run(new RoomUnitWalkToRoomUnit(user.getRoomUnit(), pet.getRoomUnit(), room, executable, executable));
    }
}
