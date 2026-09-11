package com.eu.habbo.threading.runnables;

import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomHanditemBlockSupport;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.pets.PetStatusUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserHandItemComposer;

/**
 * Hands the item the user is carrying to a pet once they have walked up to it. The pet keeps nothing:
 * it takes the item, looks at whoever gave it and is happier for it, which is what the official pet
 * menu entry does. How much a given hand item is worth as food or drink is hotel data we do not have,
 * so every item counts the same.
 */
public class HabboGiveHandItemToPet implements Runnable {
    static final int HAPPINESS = 10;

    private final Habbo from;
    private final Pet pet;

    public HabboGiveHandItemToPet(Habbo from, Pet pet) {
        this.from = from;
        this.pet = pet;
    }

    @Override
    public void run() {
        Room room = this.from.getHabboInfo().getCurrentRoom();

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
