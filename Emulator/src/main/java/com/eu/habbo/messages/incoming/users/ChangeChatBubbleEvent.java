package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.ChatStyleRepository;
import com.eu.habbo.habbohotel.users.ChatStyleSelection;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChangeChatBubbleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int chatBubble = this.packet.readInt();

        if (!this.client.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            if (isBanned(Emulator.getConfig().getValue("commands.cmd_chatcolor.banned_numbers"), chatBubble)) {
                return;
            }

            // A style the catalog sells is only for the players who bought it (AIR 13
            // RoomChatInputView offers it only when hasPurchasableChatStyle is true).
            Habbo habbo = this.client.getHabbo();
            if (ChatStyleRepository.isPurchasable(chatBubble)) {
                if (!ChatStyleRepository.owns(habbo.getHabboInfo().getId(), chatBubble)) {
                    return;
                }
            } else if (!ChatStyleSelection.isSelectable(
                    chatBubble,
                    habbo.getHabboStats().hasActiveClub(),
                    habbo.hasPermission(Permission.ACC_AMBASSADOR))) {
                // AIR never offers a system style, an HC style without club or an id it has no style for.
                return;
            }
        }

        this.client.getHabbo().getHabboStats().chatColor = RoomChatMessageBubbles.getBubble(chatBubble);
    }

    /**
     * Whether the setting lists the bubble. Blanks, spaces and entries that are not numbers are skipped: one of them
     * used to throw on every bubble change, for every user.
     */
    static boolean isBanned(String bannedNumbers, int chatBubble) {
        if (bannedNumbers == null) {
            return false;
        }
        for (String entry : bannedNumbers.split(";")) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                if (Integer.parseInt(value) == chatBubble) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Not a number: it bans nothing.
            }
        }
        return false;
    }
}
