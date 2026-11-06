package polaris.messages.outgoing.generic.alerts;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GenericAlertComposer extends MessageComposer {
    private final String message;

    public GenericAlertComposer(String message) {
        this.message = message;
    }

    public GenericAlertComposer(String message, User user) {
        this.message = message.replace("%username%", user.getUserInfo().getUsername());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GenericAlertComposer);

        this.response.appendString(this.message);

        return this.response;
    }

    public String getMessage() {
        return message;
    }
}
