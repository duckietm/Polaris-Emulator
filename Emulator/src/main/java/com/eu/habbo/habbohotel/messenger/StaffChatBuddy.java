package com.eu.habbo.habbohotel.messenger;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.friends.FriendChatMessageComposer;

public class StaffChatBuddy extends MessengerBuddy {
    public static final int BUDDY_ID = -1;
    public static final String PERMISSION_KEY = "acc_staff_chat";
    public static final String DISPLAY_NAME = "Staff Chat";
    public static final String DEFAULT_LOOK = "ADM";

    public StaffChatBuddy(int userOne) {
        super(BUDDY_ID, DISPLAY_NAME, DEFAULT_LOOK, (short) 0, userOne);
        this.setOnline(true);
    }

    @Override
    public void onMessageReceived(Habbo from, String message) {
        if (from == null || message == null || message.isEmpty()) return;
        // Re-check permission so a staff member who was demoted mid-session
        // can no longer broadcast to the staff channel.
        if (!from.hasPermission(PERMISSION_KEY)) return;

        if (message.charAt(0) == ':') {
            CommandHandler.handleCommand(from.getClient(), message);
            return;
        }

        var chatMessage = new Message(from.getHabboInfo().getId(), BUDDY_ID, message);
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(
                new FriendChatMessageComposer(chatMessage, BUDDY_ID, from.getHabboInfo().getId()).compose(),
                PERMISSION_KEY,
                from.getClient());
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.getId());
        message.appendString(this.getUsername());
        message.appendInt(this.getGender().equals(HabboGender.M) ? 0 : 1);
        message.appendBoolean(true);  // online
        message.appendBoolean(false); // not in room
        message.appendString(this.getLook());
        message.appendInt(0);         // category
        message.appendString("");     // motto
        message.appendString("");     // last seen
        message.appendString("");     // realname
        message.appendBoolean(true);  // offline messaging supported
        message.appendBoolean(false);
        message.appendBoolean(false);
        message.appendShort(0);       // relation
    }
}
