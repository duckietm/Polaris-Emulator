package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryService;
import com.eu.habbo.habbohotel.messenger.history.MessengerStoredMessage;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageAckComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageFailedComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageComposer;

public final class SendMessengerMessageEvent extends MessageHandler {
    @Override
    public void handle() {
        int conversationId = packet.readInt();
        int recipientId = packet.readInt();
        int confirmationId = packet.readInt();
        int type = packet.readInt();
        String message = FriendInputGuard.normalizeMessage(packet.readString());
        String metadata = packet.readString();
        int senderId = client.getHabbo().getHabboInfo().getId();

        try {
            if (!client.getHabbo().getHabboStats().allowTalk()) throw new IllegalStateException("muted");
            if (!FriendInputGuard.isValidMessageTarget(conversationId, recipientId)) throw new IllegalArgumentException("invalid message target");
            if (conversationId <= 0) {
                MessengerBuddy buddy = client.getHabbo().getMessenger().getFriend(recipientId);
                if (buddy == null) throw new SecurityException("not friends");
            }
            MessengerHistoryService history = MessengerHistoryServices.create();
            MessengerStoredMessage stored = history.sendMessage(conversationId, senderId, recipientId, type, message, metadata);
            client.sendResponse(new MessengerMessageAckComposer(confirmationId, stored));

            if (conversationId <= 0) {
                new Message(senderId, recipientId, message).run();
                Habbo recipient = Emulator.getGameEnvironment().getHabboManager().getHabbo(recipientId);
                if (recipient != null && recipient.getClient() != null) recipient.getClient().sendResponse(new MessengerMessageComposer(stored));
            } else {
                for (int memberId : history.listActiveMemberIds(conversationId, senderId)) {
                    if (memberId == senderId) continue;
                    Habbo member = Emulator.getGameEnvironment().getHabboManager().getHabbo(memberId);
                    if (member != null && member.getClient() != null) member.getClient().sendResponse(new MessengerMessageComposer(stored));
                }
            }
        } catch (SecurityException exception) {
            client.sendResponse(new MessengerMessageFailedComposer(confirmationId, 6));
        } catch (IllegalArgumentException exception) {
            client.sendResponse(new MessengerMessageFailedComposer(confirmationId, 1));
        } catch (RuntimeException exception) {
            client.sendResponse(new MessengerMessageFailedComposer(confirmationId, 7));
        }
    }
}
