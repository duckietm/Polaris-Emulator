package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomScoreComposer extends MessageComposer {
    private final int score;
    private final boolean canVote;

    public RoomScoreComposer(int score, boolean canVote) {
        this.score = score;
        this.canVote = canVote;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomScoreComposer);
        this.response.appendInt(this.score);
        this.response.appendBoolean(this.canVote);
        return this.response;
    }

    public int getScore() {
        return score;
    }

    public boolean isCanVote() {
        return canVote;
    }
}
