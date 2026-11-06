package polaris.messages.outgoing.rooms.pets;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CantScratchPetNotOldEnoughComposer extends MessageComposer {
    private final int currentAge;
    private final int requiredAge;

    public CantScratchPetNotOldEnoughComposer(int currentAge, int requiredAge) {
        this.currentAge = currentAge;
        this.requiredAge = requiredAge;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CantScratchPetNotOldEnoughComposer);
        this.response.appendInt(this.currentAge);
        this.response.appendInt(this.requiredAge);
        return this.response;
    }

    public int getCurrentAge() {
        return currentAge;
    }

    public int getRequiredAge() {
        return requiredAge;
    }
}
