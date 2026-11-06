package polaris.messages.outgoing.rooms.items.jukebox;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class JukeBoxPlaylistFullComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.JukeBoxPlaylistFullComposer);
        return this.response;
    }
}
