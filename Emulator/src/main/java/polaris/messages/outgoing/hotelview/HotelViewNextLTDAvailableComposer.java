package polaris.messages.outgoing.hotelview;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelViewNextLTDAvailableComposer extends MessageComposer {
    private final int time;
    private final int pageId;
    private final int itemId;
    private final String itemName;

    public HotelViewNextLTDAvailableComposer(int time, int pageId, int itemId, String itemName) {
        this.time = time;
        this.pageId = pageId;
        this.itemId = itemId;
        this.itemName = itemName;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelViewNextLTDAvailableComposer);
        this.response.appendInt(this.time);
        this.response.appendInt(this.pageId);
        this.response.appendInt(this.itemId);
        this.response.appendString(this.itemName);
        return this.response;
    }

    public int getTime() {
        return time;
    }

    public int getPageId() {
        return pageId;
    }

    public int getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }
}
