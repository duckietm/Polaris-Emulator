package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BotErrorComposer extends MessageComposer {
    public static final int ROOM_ERROR_BOTS_FORBIDDEN_IN_HOTEL = 0;
    public static final int ROOM_ERROR_BOTS_FORBIDDEN_IN_FLAT = 1;
    public static final int ROOM_ERROR_MAX_BOTS = 2;
    public static final int ROOM_ERROR_BOTS_SELECTED_TILE_NOT_FREE = 3;
    public static final int ROOM_ERROR_BOTS_NAME_NOT_ACCEPT = 4;

    private final int errorCode;

    public BotErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BotErrorComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
