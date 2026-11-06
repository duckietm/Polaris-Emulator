package polaris.messages.outgoing.friends;

import polaris.hotel.messenger.history.MessengerStoredMessage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class MessengerMessageAckComposer extends MessageComposer {
    private final int confirmationId;
    private final MessengerStoredMessage message;

    public MessengerMessageAckComposer(int confirmationId, MessengerStoredMessage message) {
        this.confirmationId = confirmationId;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerMessageAckComposer);
        response.appendInt(confirmationId);
        response.appendInt(Math.toIntExact(message.conversationId()));
        response.appendInt(Math.toIntExact(message.id()));
        response.appendInt(Math.toIntExact(message.createdAt()));
        return response;
    }
}
