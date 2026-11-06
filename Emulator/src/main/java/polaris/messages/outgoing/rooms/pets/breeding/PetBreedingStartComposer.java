package polaris.messages.outgoing.rooms.pets.breeding;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetBreedingStartComposer extends MessageComposer {
    private final int state;
    private final int anInt1;
    private final int anInt2;

    public PetBreedingStartComposer(int state, int anInt1, int anInt2) {
        this.state = state;
        this.anInt1 = anInt1;
        this.anInt2 = anInt2;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetBreedingStartComposer);
        this.response.appendInt(this.state);
        this.response.appendInt(this.anInt1);
        this.response.appendInt(this.anInt2);
        return this.response;
    }

    public int getState() {
        return state;
    }

    public int getAnInt1() {
        return anInt1;
    }

    public int getAnInt2() {
        return anInt2;
    }
}
