package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class EpicPopupComposer extends MessageComposer {
    public static final String LIBRARY_URL = "${image.library.url}";
    private final String assetURI;

    public EpicPopupComposer(String assetURI) {
        this.assetURI = assetURI;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EpicPopupComposer);
        this.response.appendString(this.assetURI);
        return this.response;
    }

    public String getAssetURI() {
        return assetURI;
    }
}
