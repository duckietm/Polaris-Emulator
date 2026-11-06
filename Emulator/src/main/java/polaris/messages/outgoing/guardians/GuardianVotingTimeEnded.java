package polaris.messages.outgoing.guardians;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuardianVotingTimeEnded extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuardianVotingTimeEnded);
        return this.response;
    }
}
