package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class IgnoredUsersComposer extends MessageComposer {
    private final List<String> usernames;

    public IgnoredUsersComposer(List<String> usernames) {
        this.usernames = List.copyOf(usernames);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.IgnoredUsersComposer);
        this.response.appendInt(this.usernames.size());

        for (String username : this.usernames) {
            this.response.appendString(username);
        }

        return this.response;
    }
}
