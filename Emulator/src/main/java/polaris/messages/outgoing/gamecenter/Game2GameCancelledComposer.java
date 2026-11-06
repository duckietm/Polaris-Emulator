package polaris.messages.outgoing.gamecenter;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR Game2GameCancelledMessageEvent (3493): the lobby was dropped before the
 * match started; the official handler calls gameCancelled(false).
 */
public class Game2GameCancelledComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.Game2GameCancelledComposer);
        return this.response;
    }
}
