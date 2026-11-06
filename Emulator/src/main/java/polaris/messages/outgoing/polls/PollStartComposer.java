package polaris.messages.outgoing.polls;

import polaris.hotel.polls.Poll;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

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
