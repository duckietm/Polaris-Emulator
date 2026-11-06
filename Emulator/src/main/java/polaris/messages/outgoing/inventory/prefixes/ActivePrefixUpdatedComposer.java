package polaris.messages.outgoing.inventory.prefixes;

import polaris.hotel.users.UserPrefix;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ActivePrefixUpdatedComposer extends MessageComposer {
    private final UserPrefix prefix;

    public ActivePrefixUpdatedComposer(UserPrefix prefix) {
        this.prefix = prefix;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ActivePrefixUpdatedComposer);

        if (this.prefix != null) {
            this.response.appendInt(this.prefix.getId());
            this.response.appendString(this.prefix.getText());
            this.response.appendString(this.prefix.getColor());
            this.response.appendString(this.prefix.getIcon());
            this.response.appendString(this.prefix.getEffect());
            this.response.appendString(this.prefix.getFont());
        } else {
            this.response.appendInt(0);
            this.response.appendString("");
            this.response.appendString("");
            this.response.appendString("");
            this.response.appendString("");
            this.response.appendString("");
        }

        return this.response;
    }
}
