package com.eu.habbo.messages.incoming.translation;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.translation.TranslationResultComposer;

public class TranslationTextRequestEvent extends MessageHandler {
    /** Each request with new text is an outbound HTTP call; a client may not send them in a stream. */
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        final GameClient client = this.client;
        final int requestId = this.packet.readInt();
        final String text = this.packet.readString();
        final String targetLanguage = this.packet.readString();

        Emulator.getGameEnvironment()
                .getGoogleTranslateManager()
                .translateAsync(
                        text,
                        targetLanguage,
                        response -> client.sendResponse(new TranslationResultComposer(requestId, response).compose()));
    }
}
