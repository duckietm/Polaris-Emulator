package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.ChatStyleRepository;
import polaris.hotel.users.ChatStyleSelection;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class ChangeChatBubbleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int chatBubble = this.packet.readInt();

        if (!canUse(this.client.getUser(), chatBubble)) {
            return;
        }

        this.client.getUser().getUserStats().chatColor = RoomChatMessageBubbles.getBubble(chatBubble);
    }

    /** Whether the user may wear the bubble: the packet and the chat color command both ask this. */
    public static boolean canUse(User user, int chatBubble) {
        if (user.hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            return true;
        }
        if (isBanned(Emulator.getConfig().getValue("commands.cmd_chatcolor.banned_numbers"), chatBubble)) {
            return false;
        }

        // A style the catalog sells is only for the players who bought it (AIR 13
        // RoomChatInputView offers it only when hasPurchasableChatStyle is true).
        if (ChatStyleRepository.isPurchasable(chatBubble)) {
            return ChatStyleRepository.owns(user.getUserInfo().getId(), chatBubble);
        }
        // AIR never offers a system style, an HC style without club or an id it has no style for.
        return ChatStyleSelection.isSelectable(
                chatBubble, user.getUserStats().hasActiveClub(), user.hasPermission(Permission.ACC_AMBASSADOR));
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
