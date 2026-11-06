package polaris.messages.outgoing.friends;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class MessengerMessageFailedComposer extends MessageComposer {
    private final int confirmationId;
    private final int errorCode;

    public MessengerMessageFailedComposer(int confirmationId, int errorCode) {
        this.confirmationId = confirmationId;
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerMessageFailedComposer);
        response.appendInt(confirmationId);
        response.appendInt(errorCode);
        return response;
    }
}
