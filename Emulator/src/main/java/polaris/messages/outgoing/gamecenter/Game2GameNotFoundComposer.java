package polaris.messages.outgoing.gamecenter;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR Game2GameNotFoundMessageEvent (444): the requested game does not exist. */
public class Game2GameNotFoundComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.Game2GameNotFoundComposer);
        return this.response;
    }
}
