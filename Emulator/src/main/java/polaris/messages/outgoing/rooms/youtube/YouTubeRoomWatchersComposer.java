package polaris.messages.outgoing.rooms.youtube;

import java.util.Set;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class YouTubeRoomWatchersComposer extends MessageComposer {
    private final Set<Integer> watcherIds;

    public YouTubeRoomWatchersComposer(Set<Integer> watcherIds) {
        this.watcherIds = watcherIds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YouTubeRoomWatchersComposer);
        this.response.appendInt(this.watcherIds.size());
        for (int id : this.watcherIds) {
            this.response.appendInt(id);
        }
        return this.response;
    }
}
