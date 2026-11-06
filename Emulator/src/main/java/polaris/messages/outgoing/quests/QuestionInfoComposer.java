package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.UnsupportedOutgoing;

public class QuestionInfoComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(UnsupportedOutgoing.QuestionInfoComposer);

        return this.response;
    }
}
