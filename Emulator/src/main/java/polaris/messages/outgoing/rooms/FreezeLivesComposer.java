package polaris.messages.outgoing.rooms;

import polaris.hotel.games.freeze.FreezeGamePlayer;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FreezeLivesComposer extends MessageComposer {
    private final FreezeGamePlayer gamePlayer;

    public FreezeLivesComposer(FreezeGamePlayer gamePlayer) {
        this.gamePlayer = gamePlayer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FreezeLivesComposer);
        this.response.appendInt(this.gamePlayer.getUser().getRoomUnit().getId());
        this.response.appendInt(this.gamePlayer.getLives());
        return this.response;
    }

    public FreezeGamePlayer getGamePlayer() {
        return gamePlayer;
    }
}
