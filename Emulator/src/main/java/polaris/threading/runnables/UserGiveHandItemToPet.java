package polaris.threading.runnables;

import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomHanditemBlockSupport;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.pets.PetStatusUpdateComposer;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;

/**
 * Hands the item the user is carrying to a pet once they have walked up to it. The pet keeps nothing:
 * it takes the item, looks at whoever gave it and is happier for it, which is what the official pet
 * menu entry does. How much a given hand item is worth as food or drink is hotel data we do not have,
 * so every item counts the same.
 */
public class UserGiveHandItemToPet implements Runnable {
    static final int HAPPINESS = 10;

    private final User from;
    private final Pet pet;

    public UserGiveHandItemToPet(User from, Pet pet) {
        this.from = from;
        this.pet = pet;
    }

    @Override
    public void run() {
        Room room = this.from.getUserInfo().getCurrentRoom();

        if (room == null || this.pet.getRoom() != room) return;

        if (RoomHanditemBlockSupport.isHanditemBlocked(room)) return;

        int itemId = this.from.getRoomUnit().getHandItem();

        if (itemId <= 0) return;

        this.from.getRoomUnit().setHandItem(0);
        room.sendComposer(new RoomUserHandItemComposer(this.from.getRoomUnit()).compose());

        this.pet.getRoomUnit().lookAtPoint(this.from.getRoomUnit().getCurrentLocation());
        this.pet.getRoomUnit().statusUpdate(true);
        this.pet.addHappiness(HAPPINESS);
        this.pet.randomHappyAction();

        room.sendComposer(new PetStatusUpdateComposer(this.pet).compose());
    }
}
