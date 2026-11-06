package polaris.messages.outgoing.catalog;

import java.util.Set;
import polaris.hotel.pets.PetRace;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetBreedsComposer extends MessageComposer {
    private final String petName;
    private final Set<PetRace> petRaces;

    public PetBreedsComposer(String petName, Set<PetRace> petRaces) {
        this.petName = petName;
        this.petRaces = petRaces;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.petRaces == null) return null;
        this.response.init(Outgoing.PetBreedsComposer);
        this.response.appendString(this.petName);
        this.response.appendInt(this.petRaces.size());
        for (PetRace race : this.petRaces) {
            this.response.appendInt(race.race);
            this.response.appendInt(race.colorOne);
            this.response.appendInt(race.colorTwo);
            this.response.appendBoolean(race.hasColorOne);
            this.response.appendBoolean(race.hasColorTwo);
            this.response.appendBoolean(race.clubOnly);
        }
        return this.response;
    }

    public String getPetName() {
        return petName;
    }

    public Set<PetRace> getPetRaces() {
        return petRaces;
    }
}
