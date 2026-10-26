package com.eu.habbo.messages.incoming.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guides.GuideChatMessage;
import com.eu.habbo.habbohotel.guides.GuideTour;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guides.GuideSessionMessageComposer;

public class GuideUserMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        GuideTour tour = environment().getGuideManager().getActiveGuideTourByHabbo(habbo);

        if (tour == null) return;

        String text = normalize(this.packet.readString());

        // Messages had no length limit, no word filter and no pace, and kept flowing to the former helper after
        // the tour ended.
        if (text.isEmpty() || !tour.allowMessage(habbo.getHabboInfo().getId(), System.currentTimeMillis())) return;

        text = environment().getWordFilter().filter(text, habbo);

        GuideChatMessage chatMessage =
                new GuideChatMessage(habbo.getHabboInfo().getId(), text, Emulator.getIntUnixTimestamp());
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

    private static void send(Habbo habbo, ServerMessage message) {
        GameClient client = habbo == null ? null : habbo.getClient();

        if (client != null) client.sendResponse(message);
    }

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }
}
