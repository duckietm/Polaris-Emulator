package polaris.messages.outgoing.rooms.pets.breeding;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetBreedingCompleted extends MessageComposer {
    // match your Nitro parser constants
    public static final int STATE_CANCEL = 1;
    public static final int STATE_ACCEPT = 2;
    public static final int STATE_REQUEST = 3;

    private final int state;
    private final int ownPetId;
    private final int otherPetId;

    public PetBreedingCompleted(int state, int ownPetId, int otherPetId) {
        this.state = state;
        this.ownPetId = ownPetId;
        this.otherPetId = otherPetId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetBreedingCompleted); // 2527
        this.response.appendInt(this.state);
        this.response.appendInt(this.ownPetId);
        this.response.appendInt(this.otherPetId);
        return this.response;
    }
}
