package polaris.plugin.events.emulator;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.plugin.events.users.UserEvent;

public class OutgoingPacketEvent extends UserEvent {
    private final MessageComposer composer;
    private final ServerMessage originalMessage;
    private ServerMessage customMessage;

    public OutgoingPacketEvent(User user, MessageComposer composer, ServerMessage originalMessage) {
        super(user);
        this.composer = composer;
        this.originalMessage = originalMessage;
        this.customMessage = null;
    }

    public ServerMessage getOriginalMessage() {
        return originalMessage;
    }

    public MessageComposer getComposer() {
        return composer;
    }

    public void setCustomMessage(ServerMessage customMessage) {
        this.customMessage = customMessage;
    }

    public boolean hasCustomMessage() {
        return this.customMessage != null;
    }

    public ServerMessage getCustomMessage() {
        return this.customMessage;
    }
}
