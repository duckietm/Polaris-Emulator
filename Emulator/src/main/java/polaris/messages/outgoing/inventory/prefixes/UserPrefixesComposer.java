package polaris.messages.outgoing.inventory.prefixes;

import java.util.List;
import polaris.hotel.users.User;
import polaris.hotel.users.UserPrefix;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserPrefixesComposer extends MessageComposer {
    private final User user;

    public UserPrefixesComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.user == null) return null;

        List<UserPrefix> prefixes =
                this.user.getInventory().getPrefixesComponent().getPrefixes();

        this.response.init(Outgoing.UserPrefixesComposer);
        this.response.appendInt(prefixes.size());

        for (UserPrefix prefix : prefixes) {
            this.response.appendInt(prefix.getId());
            this.response.appendString(prefix.getText());
            this.response.appendString(prefix.getColor());
            this.response.appendString(prefix.getIcon());
            this.response.appendString(prefix.getEffect());
            this.response.appendString(prefix.getFont());
            this.response.appendInt(prefix.isActive() ? 1 : 0);
        }

        return this.response;
    }
}
