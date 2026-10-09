package com.eu.habbo.messages.outgoing.polls;

import com.eu.habbo.habbohotel.polls.Poll;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class PollStartComposer extends MessageComposer {
    private final Poll poll;

    public PollStartComposer(Poll poll) {
        this.poll = poll;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PollStartComposer);
        // id, type, headline, summary; the thanks message belongs to the contents packet.
        this.response.appendInt(this.poll.id);
        this.response.appendString("");
        this.response.appendString(this.poll.title);
        this.response.appendString("");
        return this.response;
    }

    public Poll getPoll() {
        return poll;
    }
}
