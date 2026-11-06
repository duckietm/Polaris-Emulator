package polaris.messages.outgoing.translation;

import polaris.hotel.translations.GoogleTranslateManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TranslationLanguagesComposer extends MessageComposer {
    private final GoogleTranslateManager.SupportedLanguagesResponse responseData;

    public TranslationLanguagesComposer(GoogleTranslateManager.SupportedLanguagesResponse responseData) {
        this.responseData = responseData;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TranslationLanguagesComposer);
        this.response.appendBoolean(this.responseData != null && this.responseData.isSuccess());
        this.response.appendString(this.responseData != null ? this.responseData.getErrorMessage() : "Unknown error");

        int count =
                (this.responseData != null) ? this.responseData.getLanguages().size() : 0;
        this.response.appendInt(count);

        if (this.responseData != null) {
            for (GoogleTranslateManager.SupportedLanguage language : this.responseData.getLanguages()) {
                this.response.appendString(language.getCode());
                this.response.appendString(language.getName());
            }
        }

        return this.response;
    }
}
