package polaris.messages.outgoing.rooms.competition;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CompetitionVotingInfoComposer extends MessageComposer {
    private final int unknownInt1;
    private final String unknownString1;
    private final int unknownInt2;
    private final int unknownInt3;

    public CompetitionVotingInfoComposer(int unknownInt1, String unknownString1, int unknownInt2, int unknownInt3) {
        this.unknownInt1 = unknownInt1;
        this.unknownString1 = unknownString1;
        this.unknownInt2 = unknownInt2;
        this.unknownInt3 = unknownInt3;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CompetitionVotingInfoComposer);
        this.response.appendInt(this.unknownInt1);
        this.response.appendString(this.unknownString1);
        this.response.appendInt(this.unknownInt2);
        this.response.appendInt(this.unknownInt3);
        return this.response;
    }

    public int getUnknownInt1() {
        return unknownInt1;
    }

    public String getUnknownString1() {
        return unknownString1;
    }

    public int getUnknownInt2() {
        return unknownInt2;
    }

    public int getUnknownInt3() {
        return unknownInt3;
    }
}
