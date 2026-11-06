package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WelcomeGiftChangeEmailResultComposer extends MessageComposer {
    public static final int EMAIL_INVALID = 0;
    public static final int EMAIL_LENGTH_EXCEEDED = 1;
    public static final int EMAIL_IN_USE = 3;
    public static final int EMAIL_LIMIT_CHANGE = 4;

    private final int error;

    public WelcomeGiftChangeEmailResultComposer(int error) {
        this.error = error;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WelcomeGiftChangeEmailResultComposer);
        this.response.appendInt(this.error);
        return this.response;
    }

    public int getError() {
        return error;
    }
}
