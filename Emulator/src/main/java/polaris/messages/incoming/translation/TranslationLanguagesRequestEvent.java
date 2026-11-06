package polaris.messages.incoming.translation;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.translations.GoogleTranslateManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.translation.TranslationLanguagesComposer;

public class TranslationLanguagesRequestEvent extends MessageHandler {
    @Override
    public void handle() {
        final GameClient client = this.client;
        final String displayLanguage = this.packet.readString();

        Emulator.getThreading().run(() -> {
            GoogleTranslateManager.SupportedLanguagesResponse response =
                    Emulator.getGameEnvironment().getGoogleTranslateManager().getSupportedLanguages(displayLanguage);

            client.sendResponse(new TranslationLanguagesComposer(response).compose());
        });
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
