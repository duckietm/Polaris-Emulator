package polaris.messages.outgoing.polls.infobus;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SimplePollAnswersComposer extends MessageComposer {
    private final int no;
    private final int yes;

    public SimplePollAnswersComposer(int no, int yes) {
        this.no = no;
        this.yes = yes;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SimplePollAnswersComposer);
        this.response.appendInt(-1);
        this.response.appendInt(2);
        this.response.appendString("0");
        this.response.appendInt(this.no);
        this.response.appendString("1");
        this.response.appendInt(this.yes);
        return this.response;
    }

    public int getNo() {
        return no;
    }

    public int getYes() {
        return yes;
    }
}
