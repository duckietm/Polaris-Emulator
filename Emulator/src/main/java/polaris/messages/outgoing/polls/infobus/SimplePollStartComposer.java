package polaris.messages.outgoing.polls.infobus;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SimplePollStartComposer extends MessageComposer {
    public final int duration;
    public final String question;

    public SimplePollStartComposer(int duration, String question) {
        this.duration = duration;
        this.question = question;
    }
    // :test 3047 s:a i:10 i:20 i:10000 i:1 i:1 i:3 s:abcdefghijklmnopqrstuvwxyz12345678901234? i:1 s:a s:b

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SimplePollStartComposer);
        this.response.appendString(this.question);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(this.duration); // duration
        this.response.appendInt(-1); // Id
        this.response.appendInt(0); // Number
        this.response.appendInt(3); // Type
        this.response.appendString(this.question);
        return this.response;
    }

    public int getDuration() {
        return duration;
    }

    public String getQuestion() {
        return question;
    }
}
