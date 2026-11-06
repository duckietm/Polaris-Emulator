package polaris.messages.outgoing.quests;

import java.util.List;
import polaris.hotel.quests.RewardTrackAdmin;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** The furni matching an editor search: the query back, then name, sprite id and floor/wall code of each match. */
public class RewardTrackFurniSearchResultComposer extends MessageComposer {
    private final String query;
    private final List<RewardTrackAdmin.FurniMatch> matches;

    public RewardTrackFurniSearchResultComposer(String query, List<RewardTrackAdmin.FurniMatch> matches) {
        this.query = query == null ? "" : query;
        this.matches = matches;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackFurniSearchResultComposer);
        this.response.appendString(this.query);
        this.response.appendInt(this.matches.size());
        for (RewardTrackAdmin.FurniMatch match : this.matches) {
            this.response.appendString(match.name());
            this.response.appendInt(match.spriteId());
            this.response.appendString(match.typeCode());
        }
        return this.response;
    }
}
