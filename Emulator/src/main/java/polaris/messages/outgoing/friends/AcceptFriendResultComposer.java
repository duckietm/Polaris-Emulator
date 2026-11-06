package polaris.messages.outgoing.friends;

import java.util.Map;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AcceptFriendResultComposer extends MessageComposer {
    private final Map<Integer, Integer> errors;

    public AcceptFriendResultComposer(Map<Integer, Integer> errors) {
        this.errors = errors;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AcceptFriendResultComposer);
        this.response.appendInt(this.errors.size());
        for (Map.Entry<Integer, Integer> entry : this.errors.entrySet()) {
            this.response.appendInt(entry.getKey());
            this.response.appendInt(entry.getValue());
        }
        return this.response;
    }

    public Map<Integer, Integer> getErrors() {
        return errors;
    }
}
