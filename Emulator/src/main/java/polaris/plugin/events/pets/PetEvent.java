package polaris.plugin.events.pets;

import polaris.hotel.pets.Pet;
import polaris.plugin.Event;

public abstract class PetEvent extends Event {

    public final Pet pet;

    public PetEvent(Pet pet) {
        this.pet = pet;
    }
}
