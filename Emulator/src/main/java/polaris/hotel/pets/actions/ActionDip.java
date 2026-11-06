package polaris.hotel.pets.actions;

import java.util.Set;
import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionWater;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class ActionDip extends PetAction {
    public ActionDip() {
        super(null, true);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        Set<UserItem> waterItems = pet.getRoom().getRoomSpecialTypes().getItemsOfType(InteractionWater.class);

        if (waterItems.isEmpty()) return false;

        UserItem waterPatch =
                (UserItem) waterItems.toArray()[Emulator.getRandom().nextInt(waterItems.size())];

        pet.getRoomUnit().setGoalLocation(pet.getRoom().getLayout().getTile(waterPatch.getX(), waterPatch.getY()));

        pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));

        return true;
    }
}
