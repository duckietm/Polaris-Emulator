package polaris.messages.outgoing.users;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** The ignored users (AIR {@code ?_-A1M?/?_-02j?}): their user ids. */
public class IgnoredUsersComposer extends MessageComposer {
    private final List<Integer> userIds;

    public IgnoredUsersComposer(List<Integer> userIds) {
        this.userIds = List.copyOf(userIds);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.IgnoredUsersComposer);
        this.response.appendInt(this.userIds.size());

        for (int userId : this.userIds) {
            this.response.appendInt(userId);
        }

        return this.response;
    }
}
