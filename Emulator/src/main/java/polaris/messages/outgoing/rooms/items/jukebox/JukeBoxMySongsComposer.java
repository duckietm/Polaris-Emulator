package polaris.messages.outgoing.rooms.items.jukebox;

import java.util.List;
import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class JukeBoxMySongsComposer extends MessageComposer {
    private final List<InteractionMusicDisc> items;

    public JukeBoxMySongsComposer(List<InteractionMusicDisc> items) {
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.JukeBoxMySongsComposer);

        this.response.appendInt(this.items.size());

        for (UserItem item : this.items) {
            this.response.appendInt(BuildersClubItemIds.toWire(item.getId()));
            this.response.appendInt(((InteractionMusicDisc) item).getSongId());
        }

        return this.response;
    }

    public List<InteractionMusicDisc> getItems() {
        return items;
    }
}
