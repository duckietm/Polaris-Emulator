package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendsComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerInitComposer;

import java.util.ArrayList;

public class RequestInitFriendsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        var messages = new ArrayList<ServerMessage>();
        messages.add(new MessengerInitComposer(this.client.getHabbo()).compose());
        messages.addAll(FriendsComposer.getMessagesForBuddyList(this.client.getHabbo().getMessenger().getFriends().values()));
        this.client.sendResponses(messages);
    }
}
