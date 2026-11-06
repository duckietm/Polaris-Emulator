package polaris.messages.outgoing.polls;

import polaris.hotel.polls.Poll;
import polaris.hotel.polls.PollQuestion;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PollQuestionsComposer extends MessageComposer {
    private final Poll poll;

    public PollQuestionsComposer(Poll poll) {
        this.poll = poll;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PollQuestionsComposer);

        this.response.appendInt(this.poll.id);
        this.response.appendString(this.poll.title);
        this.response.appendString(this.poll.thanksMessage);
        this.response.appendInt(this.poll.getQuestions().size());
        for (PollQuestion question : this.poll.getQuestions()) {
            question.serialize(this.response);
        }

        this.response.appendBoolean(true);
        return this.response;
    }

    public Poll getPoll() {
        return poll;
    }
}
