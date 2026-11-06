package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetErrorComposer extends MessageComposer {
    public static final int ROOM_ERROR_PETS_FORBIDDEN_IN_HOTEL = 0;
    public static final int ROOM_ERROR_PETS_FORBIDDEN_IN_FLAT = 1;
    public static final int ROOM_ERROR_MAX_PETS = 2;
    public static final int ROOM_ERROR_PETS_SELECTED_TILE_NOT_FREE = 3;
    public static final int ROOM_ERROR_PETS_NO_FREE_TILES = 4;
    public static final int ROOM_ERROR_MAX_OWN_PETS = 5;

    private final int errorCode;

    public PetErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetErrorComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
