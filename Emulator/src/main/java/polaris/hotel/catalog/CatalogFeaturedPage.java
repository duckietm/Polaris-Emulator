package polaris.hotel.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import polaris.Emulator;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;

public class CatalogFeaturedPage implements ISerialize {
    private final int slotId;
    private final String caption;
    private final String image;
    private final Type type;
    private final int expireTimestamp;
    private final String pageName;
    private final int pageId;
    private final String productName;

    public CatalogFeaturedPage(
            int slotId,
            String caption,
            String image,
            Type type,
            int expireTimestamp,
            String pageName,
            int pageId,
            String productName) {
        this.slotId = slotId;
        this.caption = caption;
        this.image = image;
        this.type = type;
        this.expireTimestamp = expireTimestamp;
        this.pageName = pageName;
        this.pageId = pageId;
        this.productName = productName;
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.slotId);
        message.appendString(this.caption);
        message.appendString(this.image);
        message.appendInt(this.type.type);
        switch (this.type) {
            case PAGE_NAME:
                message.appendString(this.pageName);
                break;
            case PAGE_ID:
                message.appendInt(this.pageId);
                break;
            case PRODUCT_NAME:
                message.appendString(this.productName);
                break;
        }
        message.appendInt(secondsLeft(this.expireTimestamp, Emulator.getIntUnixTimestamp()));
    }

    public int getSlotId() {
        return this.slotId;
    }

    /**
     * The slots sorted by slot id. They are kept in a hash map, whose order is not the slot order (slots 1 to 4 came
     * out as 3, 1, 4, 2), and the client shows them in the order they are sent.
     */
    static List<CatalogFeaturedPage> inSlotOrder(Collection<CatalogFeaturedPage> slots) {
        return shown(slots, 0);
    }

    /**
     * The slots the client gets at `now`: sorted by slot id, without those whose end date has passed. The client
     * reads 0 seconds left as "never ends", so an ended slot that was still sent stayed on the front page for good.
     * A `now` of 0 keeps every slot.
     */
    static List<CatalogFeaturedPage> shown(Collection<CatalogFeaturedPage> slots, int now) {
        List<CatalogFeaturedPage> sorted = new ArrayList<>(slots);
        // The map is public: a plugin may have put an empty slot in it.
        sorted.removeIf(slot -> slot == null || (now > 0 && slot.expireTimestamp > 0 && slot.expireTimestamp <= now));
        sorted.sort(Comparator.comparingInt(CatalogFeaturedPage::getSlotId));
        return sorted;
    }

    /** The client reads the seconds until the slot expires; 0 means it does not expire. */
    static int secondsLeft(int expireTimestamp, int now) {
        return (expireTimestamp <= 0) ? 0 : Math.max(0, expireTimestamp - now);
    }

    public enum Type {
        PAGE_NAME(0),
        PAGE_ID(1),
        PRODUCT_NAME(2);

        public final int type;

        Type(int type) {
            this.type = type;
        }
    }
}
