package polaris.messages.outgoing.guilds;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildEditFailComposer extends MessageComposer {
    public static final int ROOM_ALREADY_IN_USE = 0;
    public static final int INVALID_GUILD_NAME = 1;
    public static final int HC_REQUIRED = 2;
    public static final int MAX_GUILDS_JOINED = 3;

    private int errorCode;

    public GuildEditFailComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildEditFailComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
