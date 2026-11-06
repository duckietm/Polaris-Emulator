package polaris.messages.outgoing.generic.alerts;

import java.util.ArrayList;
import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MessagesForYouComposer extends MessageComposer {
    private final String[] messages;
    private final List<String> newMessages;

    public MessagesForYouComposer(String[] messages) {
        this.messages = messages;
        this.newMessages = new ArrayList<>();
    }

    public MessagesForYouComposer(List<String> newMessages) {
        this.newMessages = newMessages;
        this.messages = new String[] {};
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MessagesForYouComposer);
        this.response.appendInt(this.messages.length + this.newMessages.size());

        for (String s : this.messages) {
            this.response.appendString(s);
        }

        for (String s : this.newMessages) {
            this.response.appendString(s);
        }

        return this.response;
    }

    public String[] getMessages() {
        return messages;
    }

    public List<String> getNewMessages() {
        return newMessages;
    }
}
