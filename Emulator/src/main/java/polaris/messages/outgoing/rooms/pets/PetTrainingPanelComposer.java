package polaris.messages.outgoing.rooms.pets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetCommand;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetTrainingPanelComposer extends MessageComposer {
    private final Pet pet;

    public PetTrainingPanelComposer(Pet pet) {
        this.pet = pet;
    }

    @Override
    protected ServerMessage composeInternal() {
        List<PetCommand> enabled = new ArrayList<>();
        Collections.sort(this.pet.getPetData().getPetCommands());

        this.response.init(Outgoing.PetTrainingPanelComposer);
        this.response.appendInt(this.pet.getId());
        this.response.appendInt(this.pet.getPetData().getPetCommands().size());

        for (PetCommand petCommand : this.pet.getPetData().getPetCommands()) {
            this.response.appendInt(petCommand.id);

            if (this.pet.getLevel() >= petCommand.level) {
                enabled.add(petCommand);
            }
        }

        if (!enabled.isEmpty()) {
            Collections.sort(enabled);
        }

        this.response.appendInt(enabled.size());

        for (PetCommand petCommand : enabled) {
            this.response.appendInt(petCommand.id);
        }

        return this.response;
    }

    public Pet getPet() {
        return pet;
    }
}
