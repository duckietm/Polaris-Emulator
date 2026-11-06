package polaris.hotel.items.interactions.wired.effects;

import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;

/**
 * The optional message a user-targeting box carries ("mute user", "sit", "lay", "fast walk"): a
 * whisper to each affected user with the placeholders the show-message box expands. Kept in one
 * place so the posture boxes, which saved the message and never sent it, say the same thing "mute
 * user" always did.
 */
final class WiredEffectUserMessage {
    private WiredEffectUserMessage() {}

    static void whisper(WiredContext ctx, User user, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        if (user == null || user.getUserInfo() == null || user.getClient() == null) {
            return;
        }

        String username = user.getUserInfo().getUsername();
        String text = message.replace("%user%", username == null ? "" : username);

        // The hotel-wide counters cost a lookup each, so they are only resolved when asked for.
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (text.contains("%online_count%")) {
            int online =
                    (environment == null) ? 0 : environment.getUserManager().getOnlineCount();
            text = text.replace("%online_count%", online + "");
        }
        if (text.contains("%room_count%")) {
            int rooms = (environment == null)
                    ? 0
                    : environment.getRoomManager().getActiveRooms().size();
            text = text.replace("%room_count%", rooms + "");
        }

        text = WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, text);
        user.getClient()
                .sendResponse(new RoomUserWhisperComposer(
                        new RoomChatMessage(text, user, user, RoomChatMessageBubbles.WIRED)));
    }
}
