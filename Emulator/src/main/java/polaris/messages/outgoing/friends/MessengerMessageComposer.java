package polaris.messages.outgoing.friends;

import polaris.hotel.messenger.history.MessengerStoredMessage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class MessengerMessageComposer extends MessageComposer {
    private final MessengerStoredMessage message;

    public MessengerMessageComposer(MessengerStoredMessage message) {
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerMessageComposer);
        response.appendInt(Math.toIntExact(message.conversationId()));
        MessengerHistoryComposer.appendMessage(response, message);
        return response;
    }
}
