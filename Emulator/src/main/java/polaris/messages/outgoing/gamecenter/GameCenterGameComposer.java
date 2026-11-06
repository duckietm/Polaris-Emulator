package polaris.messages.outgoing.gamecenter;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GameCenterGameComposer extends MessageComposer {
    public static final int OK = 0;
    public static final int ERROR = 1;

    public final int gameId;
    public final int status;

    public GameCenterGameComposer(int gameId, int status) {
        this.gameId = gameId;
        this.status = status;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GameCenterGameComposer);
        this.response.appendInt(this.gameId);
        this.response.appendInt(this.status);
        return this.response;
    }

    public int getGameId() {
        return gameId;
    }

    public int getStatus() {
        return status;
    }
}
