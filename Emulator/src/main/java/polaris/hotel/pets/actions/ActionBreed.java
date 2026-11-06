package polaris.hotel.pets.actions;

import org.apache.commons.lang3.Strings;
import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.pets.breeding.PetBreedingStartFailedComposer;

public class ActionBreed extends PetAction {
    public ActionBreed() {
        super(PetTasks.BREED, true);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        InteractionPetBreedingNest nest = null;
        for (UserItem item : pet.getRoom().getRoomSpecialTypes().getItemsOfType(InteractionPetBreedingNest.class)) {
            if (Strings.CI.contains(
                    item.getBaseItem().getName(), pet.getPetData().getName())) {
                if (!((InteractionPetBreedingNest) item).boxFull()) {
                    nest = (InteractionPetBreedingNest) item;
                    break;
                }
            }
        }

        if (nest != null) {
            pet.getRoomUnit().setGoalLocation(pet.getRoom().getLayout().getTile(nest.getX(), nest.getY()));
            pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_HAPPY));

            return true;
        } else {
            user.getClient().sendResponse(new PetBreedingStartFailedComposer(PetBreedingStartFailedComposer.NO_NESTS));
        }

        return false;
    }
}
