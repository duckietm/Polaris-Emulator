package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MutedWhisperComposer extends MessageComposer {
    private final int seconds;

    public MutedWhisperComposer(int seconds) {
        this.seconds = Math.max(0, seconds);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MutedWhisperComposer);
        this.response.appendInt(this.seconds);
        return this.response;
    }

    public int getSeconds() {
        return seconds;
    }
}
