package polaris.messages.outgoing.guardians;

import java.util.ArrayList;
import polaris.hotel.guides.GuardianTicket;
import polaris.hotel.guides.GuardianVote;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuardianVotingVotesComposer extends MessageComposer {
    private final GuardianTicket ticket;
    private final User guardian;

    public GuardianVotingVotesComposer(GuardianTicket ticket, User guardian) {
        this.ticket = ticket;
        this.guardian = guardian;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuardianVotingVotesComposer);

        ArrayList<GuardianVote> votes = this.ticket.getSortedVotes(this.guardian);

        this.response.appendInt(votes.size());

        for (GuardianVote vote : votes) {
            this.response.appendInt(vote.type.getType());
        }

        return this.response;
    }

    public GuardianTicket getTicket() {
        return ticket;
    }

    public User getGuardian() {
        return guardian;
    }
}
