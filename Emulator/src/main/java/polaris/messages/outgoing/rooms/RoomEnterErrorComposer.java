package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomEnterErrorComposer extends MessageComposer {
    public static final int ROOM_ERROR_GUESTROOM_FULL = 1;
    public static final int ROOM_ERROR_CANT_ENTER = 2;
    public static final int ROOM_ERROR_QUE = 3;
    public static final int ROOM_ERROR_BANNED = 4;

    public static final String ROOM_NEEDS_VIP = "c";
    public static final String EVENT_USERS_ONLY = "e1";
    public static final String ROOM_LOCKED = "na";
    public static final String TO_MANY_SPECTATORS = "spectator_mode_full";

    private final int errorCode;
    private final String queError;

    public RoomEnterErrorComposer(int errorCode) {
        this.errorCode = errorCode;
        this.queError = "";
    }

    public RoomEnterErrorComposer(int errorCode, String queError) {
        this.errorCode = errorCode;
        this.queError = queError;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomEnterErrorComposer);
        this.response.appendInt(this.errorCode);
        this.response.appendString(this.queError);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getQueError() {
        return queError;
    }
}
