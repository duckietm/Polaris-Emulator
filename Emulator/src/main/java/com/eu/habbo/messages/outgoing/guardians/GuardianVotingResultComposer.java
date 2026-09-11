package com.eu.habbo.messages.outgoing.guardians;

import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.habbohotel.guides.GuardianVote;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Map;

public class GuardianVotingResultComposer extends MessageComposer {
    private final GuardianTicket ticket;
    private final GuardianVote vote;

    public GuardianVotingResultComposer(GuardianTicket ticket, GuardianVote vote) {
        this.ticket = ticket;
        this.vote = vote;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuardianVotingResultComposer);
        // winningVoteCode / ownVoteCode are vote codes (0 ok, 1 bad, 2 very bad, -1 forwarded);
        // only the trailing status list below uses the raw GuardianVoteType ordinal.
        this.response.appendInt(this.ticket.getVerdict().getVoteCode()); // Final Verdict
        this.response.appendInt(this.vote.type.getVoteCode()); // Your vote

        this.response.appendInt(this.ticket.getVotes().size() - 1); // Other votes count.

        for (Map.Entry<Habbo, GuardianVote> set : this.ticket.getVotes().entrySet()) {
            if (set.getValue().equals(this.vote)) continue;

            this.response.appendInt(set.getValue().type.getType());
        }
        return this.response;
    }

    public GuardianTicket getTicket() {
        return ticket;
    }

    public GuardianVote getVote() {
        return vote;
    }
}
