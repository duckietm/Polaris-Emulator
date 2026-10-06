package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.messenger.StaffChatBuddy;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryService;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.habbohotel.messenger.history.MessengerStoredMessage;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageAckComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageFailedComposer;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;

public final class SendMessengerMessageEvent extends MessageHandler {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SendMessengerMessageEvent.class);
    private static final int TEXT_MESSAGE = 0;

    @Override
    public void handle() {
        int conversationId = packet.readInt();
        int recipientId = packet.readInt();
        int confirmationId = packet.readInt();
        int type = packet.readInt();
        String message = FriendInputGuard.normalizeMessage(packet.readString());
        String metadata = packet.readString();
        int senderId = client.getHabbo().getHabboInfo().getId();
        boolean hasStaffChat = client.getHabbo().hasPermission(StaffChatBuddy.PERMISSION_KEY);

        try {
            if (!client.getHabbo().getHabboStats().allowTalk()) throw new IllegalStateException("muted");
            if (!FriendInputGuard.isValidMessageTarget(conversationId, recipientId, hasStaffChat))
                throw new IllegalArgumentException("invalid message target");
            // Same per-user console limit as the Flash message packet.
            if (client.getHabbo().getHabboStats().consoleMessageFlooded(System.currentTimeMillis()))
                throw new IllegalStateException("flood");
            if (conversationId <= 0) {
                MessengerBuddy buddy = client.getHabbo().getMessenger().getFriend(recipientId);
                if (buddy == null) throw new SecurityException("not friends");
                if (FriendInputGuard.isStaffChatTarget(recipientId, hasStaffChat)) {
                    this.sendStaffChat(buddy, confirmationId, type, message, metadata);
                    return;
                }
            }
            HabbiconService.Item habbicon = null;
            if (type == MessengerHistoryService.HABBICON_MESSAGE) {
                habbicon = client.getHabbo().getHabbiconService().load(senderId).requireItem(Integer.parseInt(message));
                if (!habbicon.owned() || !metadata.isEmpty()) {
                    throw new SecurityException("Habbicon is not owned");
                }
            }
            MessengerHistoryService history = MessengerHistoryServices.create();
            MessengerStoredMessage stored =
                    history.sendMessage(conversationId, senderId, recipientId, type, message, metadata);
            client.sendResponse(new MessengerMessageAckComposer(confirmationId, stored));
            if (conversationId <= 0) {
                new Message(senderId, recipientId, habbicon == null ? message : ":" + habbicon.name() + ":").run();
                Habbo recipient =
                        Emulator.getGameEnvironment().getHabboManager().getHabbo(recipientId);
                if (recipient != null && recipient.getClient() != null)
                    recipient.getClient().sendResponse(new MessengerMessageComposer(stored));
            } else {
                for (int memberId : history.listActiveMemberIds(conversationId, senderId)) {
                    if (memberId == senderId) continue;
                    Habbo member =
                            Emulator.getGameEnvironment().getHabboManager().getHabbo(memberId);
                    if (member != null && member.getClient() != null)
                        member.getClient().sendResponse(new MessengerMessageComposer(stored));
                }
            }
            if (habbicon != null) {
                try {
                    HabbiconService service = client.getHabbo().getHabbiconService();
                    if (service.use(senderId, habbicon.id())) {
                        client.sendResponse(new UserHabbiconsComposer(service.load(senderId)));
                    }
                } catch (java.sql.SQLException exception) {
                    LOGGER.warn("Unable to update recent Habbicons for user {}", senderId, exception);
                }
            }
        } catch (SecurityException exception) {
            client.sendResponse(new MessengerMessageFailedComposer(confirmationId, 6));
        } catch (IllegalArgumentException exception) {
            client.sendResponse(new MessengerMessageFailedComposer(confirmationId, 1));
        } catch (java.sql.SQLException | RuntimeException exception) {
            client.sendResponse(new MessengerMessageFailedComposer(confirmationId, 7));
        }
    }

    /** Staff Chat is a broadcast, not a stored conversation: text only, acknowledged without history ids. */
    private void sendStaffChat(MessengerBuddy buddy, int confirmationId, int type, String message, String metadata) {
        if (type != TEXT_MESSAGE || message.isEmpty() || !metadata.isEmpty())
            throw new IllegalArgumentException("Staff Chat takes plain text only");

        buddy.onMessageReceived(client.getHabbo(), message);
        int senderId = client.getHabbo().getHabboInfo().getId();
        client.sendResponse(new MessengerMessageAckComposer(
                confirmationId,
                new MessengerStoredMessage(
                        0, 0, senderId, type, message, metadata, System.currentTimeMillis() / 1000)));
    }
}
