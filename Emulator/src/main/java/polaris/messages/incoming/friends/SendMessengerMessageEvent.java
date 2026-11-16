package polaris.messages.incoming.friends;

import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.messenger.Message;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.messenger.StaffChatBuddy;
import polaris.hotel.messenger.history.MessengerHistoryService;
import polaris.hotel.messenger.history.MessengerHistoryServices;
import polaris.hotel.messenger.history.MessengerStoredMessage;
import polaris.hotel.modtool.WordFilter;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.MessengerMessageAckComposer;
import polaris.messages.outgoing.friends.MessengerMessageComposer;
import polaris.messages.outgoing.friends.MessengerMessageFailedComposer;
import polaris.messages.outgoing.habbicons.UserHabbiconsComposer;

public final class SendMessengerMessageEvent extends MessageHandler {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SendMessengerMessageEvent.class);
    private static final int TEXT_MESSAGE = 0;

    /**
     * What a client may send: text or a Habbicon. The store also takes types 1-3, which the word filter never saw.
     * Text keeps its metadata (the protocol carries it back to the recipient, bounded by the store); a Habbicon is
     * refused metadata further down.
     */
    static boolean isClientMessage(int type) {
        return type == TEXT_MESSAGE || type == MessengerHistoryService.HABBICON_MESSAGE;
    }

    @Override
    public void handle() {
        int conversationId = packet.readInt();
        int recipientId = packet.readInt();
        int confirmationId = packet.readInt();
        int type = packet.readInt();
        String message = FriendInputGuard.normalizeMessage(packet.readString());
        String metadata = packet.readString();
        GameEnvironment environment = Emulator.getGameEnvironment();
        int senderId = client.getUser().getUserInfo().getId();
        boolean hasStaffChat = client.getUser().hasPermission(StaffChatBuddy.PERMISSION_KEY);

        try {
            if (!client.getUser().getUserStats().allowTalk()) throw new IllegalStateException("muted");
            if (!FriendInputGuard.isValidMessageTarget(conversationId, recipientId, hasStaffChat))
                throw new IllegalArgumentException("invalid message target");
            if (!isClientMessage(type)) throw new IllegalArgumentException("unsupported message type");
            // Same per-user console limit as the Flash message packet.
            if (client.getUser().getUserStats().consoleMessageFlooded(System.currentTimeMillis()))
                throw new IllegalStateException("flood");
            if (conversationId <= 0) {
                MessengerBuddy buddy = client.getUser().getMessenger().getFriend(recipientId);
                if (buddy == null) throw new SecurityException("not friends");
                if (FriendInputGuard.isStaffChatTarget(recipientId, hasStaffChat)) {
                    this.sendStaffChat(buddy, confirmationId, type, message, metadata);
                    return;
                }
            }
            MessengerHistoryService history = MessengerHistoryServices.create();
            if (conversationId > 0) {
                // A stored conversation stays open after an unfriend: every other member must still be a friend.
                for (int memberId : history.listActiveMemberIds(conversationId, senderId)) {
                    if (memberId != senderId && client.getUser().getMessenger().getFriend(memberId) == null)
                        throw new SecurityException("not friends");
                }
            }
            // The console packet filters every line (MessengerBuddy.onMessageReceivedWithDeliveryStatus); so does this
            // one.
            if (type == TEXT_MESSAGE && WordFilter.ENABLED_FRIENDCHAT)
                message = environment.getWordFilter().filter(message, client.getUser());
            HabbiconService.Item habbicon = null;
            if (type == MessengerHistoryService.HABBICON_MESSAGE) {
                habbicon = client.getUser().getHabbiconService().load(senderId).requireItem(Integer.parseInt(message));
                if (!habbicon.owned() || !metadata.isEmpty()) {
                    throw new SecurityException("Habbicon is not owned");
                }
            }
            MessengerStoredMessage stored =
                    history.sendMessage(conversationId, senderId, recipientId, type, message, metadata);
            client.sendResponse(new MessengerMessageAckComposer(confirmationId, stored));
            if (conversationId <= 0) {
                new Message(senderId, recipientId, habbicon == null ? message : ":" + habbicon.name() + ":").run();
                User recipient = environment.getUserManager().getUser(recipientId);
                if (recipient != null && recipient.getClient() != null)
                    recipient.getClient().sendResponse(new MessengerMessageComposer(stored));
            } else {
                for (int memberId : history.listActiveMemberIds(conversationId, senderId)) {
                    if (memberId == senderId) continue;
                    User member = environment.getUserManager().getUser(memberId);
                    if (member != null && member.getClient() != null)
                        member.getClient().sendResponse(new MessengerMessageComposer(stored));
                }
            }
            if (habbicon != null) {
                try {
                    HabbiconService service = client.getUser().getHabbiconService();
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

        buddy.onMessageReceived(client.getUser(), message);
        int senderId = client.getUser().getUserInfo().getId();
        client.sendResponse(new MessengerMessageAckComposer(
                confirmationId,
                new MessengerStoredMessage(
                        0, 0, senderId, type, message, metadata, System.currentTimeMillis() / 1000)));
    }
}
