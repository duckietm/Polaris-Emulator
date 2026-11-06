package polaris.messages.outgoing.rooms.pets.breeding;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetBreedingStartFailedComposer extends MessageComposer {
    public static final int NO_NESTS = 0;
    public static final int NO_SUITABLE_NESTS = 1;
    public static final int NEST_FULL = 2;
    public static final int NOT_OWNER = 3;
    public static final int ALREADY_IN_NEST = 4;
    public static final int NO_PATH_TO_NEST = 5;
    public static final int TOO_TIRED = 6;

    private final int reason;

    public PetBreedingStartFailedComposer(int reason) {
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetBreedingStartFailedComposer);
        this.response.appendInt(this.reason);
        return this.response;
    }

    public int getReason() {
        return reason;
    }
}
