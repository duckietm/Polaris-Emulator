package polaris.hotel.messenger;

import polaris.Emulator;
import polaris.hotel.commands.CommandHandler;
import polaris.hotel.users.User;
import polaris.messages.outgoing.friends.FriendChatMessageComposer;

public class StaffChatBuddy extends ChannelBuddy {
    public static final int BUDDY_ID = -1;
    public static final String PERMISSION_KEY = "acc_staff_chat";
    public static final String DISPLAY_NAME = "Staff Chat";
    public static final String DEFAULT_LOOK = "ADM";

    public StaffChatBuddy(int userOne) {
        super(BUDDY_ID, DISPLAY_NAME, DEFAULT_LOOK, userOne);
    }

    @Override
    public void onMessageReceived(User from, String message) {
        if (from == null || message == null || message.isEmpty()) return;
        // Re-check permission so a staff member who was demoted mid-session
        // can no longer broadcast to the staff channel.
        if (!from.hasPermission(PERMISSION_KEY)) return;

        if (message.charAt(0) == ':') {
            CommandHandler.handleChatCommand(from.getClient(), message);
            return;
        }

        Message chatMessage = new Message(from.getUserInfo().getId(), BUDDY_ID, message);
        Emulator.getGameServer()
                .getGameClientManager()
                .sendBroadcastResponse(
                        new FriendChatMessageComposer(
                                        chatMessage,
                                        BUDDY_ID,
                                        from.getUserInfo().getId())
                                .compose(),
                        PERMISSION_KEY,
                        from.getClient());
    }
}
