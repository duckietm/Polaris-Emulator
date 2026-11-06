package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormGamesLeftComposer extends MessageComposer {

    private final int gamesLeft;

    public SnowStormGamesLeftComposer(int gamesLeft) {
        this.gamesLeft = gamesLeft;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormGamesLeftComposer);
        this.response.appendInt(this.gamesLeft);
        return this.response;
    }
}
