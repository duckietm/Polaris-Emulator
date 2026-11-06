package polaris.messages.outgoing.catalog.marketplace;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MarketplaceItemPostedComposer extends MessageComposer {
    public static final int POST_SUCCESS = 1;
    public static final int FAILED_TECHNICAL_ERROR = 2;
    public static final int MARKETPLACE_DISABLED = 3;
    public static final int ITEM_JUST_ADDED_TO_SHOP = 4;

    private final int code;

    public MarketplaceItemPostedComposer(int code) {
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceItemPostedComposer);
        this.response.appendInt(this.code);
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
