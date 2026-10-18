package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

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
