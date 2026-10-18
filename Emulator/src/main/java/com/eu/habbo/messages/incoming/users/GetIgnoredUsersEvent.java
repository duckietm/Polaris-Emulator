package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.IgnoredUsersComposer;

/** Sends the user's ignored user ids (AIR {@code ?_-02R?/?_-Y2p?}, no fields). */
public class GetIgnoredUsersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(
                new IgnoredUsersComposer(this.client.getHabbo().getHabboStats().getIgnoredUsers()));
    }
}
