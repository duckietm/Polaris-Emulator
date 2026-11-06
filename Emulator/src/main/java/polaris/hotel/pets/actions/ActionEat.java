package polaris.hotel.pets.actions;

import polaris.hotel.items.Item;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.users.User;

public class ActionEat extends PetAction {
    public ActionEat() {
        // stopsPetWalking=false so pet can walk to food
        // Don't set EAT status here - let InteractionPetFood.onWalkOn() handle it when pet arrives
        super(null, false);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        // Check if pet is hungry enough to want food (threshold: 35 to match InteractionPetFood)
        if (pet.getLevelHunger() >= 35) {
            // Check if there's food available in the room before sending pet to eat
            if (pet.getRoom() != null && pet.getRoom().getRoomSpecialTypes() != null) {
                Item foodItem = pet.findFood();

                if (foodItem != null) {
                    // Food exists - pet goes to eat
                    pet.say(pet.getPetData().randomVocal(PetVocalsType.HUNGRY));
                    pet.eat();
                    return true;
                } else {
                    // No suitable food in room - pet complains
                    pet.say(pet.getPetData().randomVocal(PetVocalsType.HUNGRY));
                    return false;
                }
            }
            return false;
        } else {
            // Pet is not hungry - disobeys command
            pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
            return false;
        }
    }
}
