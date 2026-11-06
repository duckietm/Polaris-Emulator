package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guides.GuideChatMessage;
import polaris.hotel.guides.GuideTour;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideSessionMessageComposer;

public class GuideUserMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        GuideTour tour = environment().getGuideManager().getActiveGuideTourByUser(user);

        if (tour == null) return;

        String text = normalize(this.packet.readString());

        // Messages had no length limit, no word filter and no pace, and kept flowing to the former helper after
        // the tour ended.
        if (text.isEmpty() || !tour.allowMessage(user.getUserInfo().getId(), System.currentTimeMillis())) return;

        text = environment().getWordFilter().filter(text, user);

        GuideChatMessage chatMessage =
                new GuideChatMessage(user.getUserInfo().getId(), text, Emulator.getIntUnixTimestamp());
        tour.addMessage(chatMessage);
        ServerMessage serverMessage = new GuideSessionMessageComposer(chatMessage).compose();
        send(tour.getHelper(), serverMessage);
        send(tour.getNoob(), serverMessage);
    }

    static String normalize(String text) {
        String trimmed = text == null ? "" : text.trim();

        return trimmed.length() > GuideTour.MAX_MESSAGE_LENGTH
                ? trimmed.substring(0, GuideTour.MAX_MESSAGE_LENGTH)
                : trimmed;
    }

    private static void send(User user, ServerMessage message) {
        GameClient client = user == null ? null : user.getClient();

        if (client != null) client.sendResponse(message);
    }

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }
}
