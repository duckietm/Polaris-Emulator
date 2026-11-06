package polaris.messages.outgoing.inventory.prefixes;

import polaris.hotel.users.UserPrefix;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PrefixReceivedComposer extends MessageComposer {
    private final UserPrefix prefix;

    public PrefixReceivedComposer(UserPrefix prefix) {
        this.prefix = prefix;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PrefixReceivedComposer);
        this.response.appendInt(this.prefix.getId());
        this.response.appendString(this.prefix.getText());
        this.response.appendString(this.prefix.getColor());
        this.response.appendString(this.prefix.getIcon());
        this.response.appendString(this.prefix.getEffect());
        this.response.appendString(this.prefix.getFont());
        return this.response;
    }
}
