package polaris.hotel.pets.actions;

import polaris.Emulator;
import polaris.hotel.items.interactions.pets.InteractionPetTree;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.threading.runnables.PetClearPosture;

public class ActionHang extends PetAction {
    public ActionHang() {
        super(PetTasks.FREE, true);
        this.minimumActionDuration = 4000;
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        // Hang requires pet to be on a pet tree (dragon/monkey tree)
        if (pet.getRoom() == null) {
            return false;
        }

        UserItem itemBelow = pet.getRoom()
                .getTopItemAt(pet.getRoomUnit().getX(), pet.getRoomUnit().getY());
        if (!(itemBelow instanceof InteractionPetTree)) {
            // Pet must go to tree first
            pet.findTree();
            pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
            return false;
        }

        pet.clearPosture();
        pet.getRoomUnit().setStatus(RoomUnitStatus.HANG, "");

        Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.HANG, null, false), 4000);

        if (pet.getHappiness() > 50) pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        else pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));

        return true;
    }
}
