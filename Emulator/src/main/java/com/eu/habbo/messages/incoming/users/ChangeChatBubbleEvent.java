package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.ChatStyleRepository;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChangeChatBubbleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int chatBubble = this.packet.readInt();

        if (!this.client.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            for (String s : Emulator.getConfig()
                    .getValue("commands.cmd_chatcolor.banned_numbers")
                    .split(";")) {
                if (Integer.parseInt(s) == chatBubble) {
                    return;
                }
            }

            // A style the catalog sells is only for the players who bought it (AIR 13
            // RoomChatInputView offers it only when hasPurchasableChatStyle is true).
            if (ChatStyleRepository.isPurchasable(chatBubble)
                    && !ChatStyleRepository.owns(
                            this.client.getHabbo().getHabboInfo().getId(), chatBubble)) {
                return;
            }
        }

        this.client.getHabbo().getHabboStats().chatColor = RoomChatMessageBubbles.getBubble(chatBubble);
    }
}
