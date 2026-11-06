package polaris.messages.outgoing.gamecenter;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR Game2UserBlockedMessageEvent (3508): the remaining seconds of the
 * leave-game block, counted down on the play button.
 */
public class Game2UserBlockedComposer extends MessageComposer {

    private final int playerBlockLength;

    public Game2UserBlockedComposer(int playerBlockLength) {
        this.playerBlockLength = playerBlockLength;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.Game2UserBlockedComposer);
        this.response.appendInt(this.playerBlockLength);
        return this.response;
    }

    public int getPlayerBlockLength() {
        return playerBlockLength;
    }
}
