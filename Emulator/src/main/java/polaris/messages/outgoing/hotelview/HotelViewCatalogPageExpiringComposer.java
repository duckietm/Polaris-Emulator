package polaris.messages.outgoing.hotelview;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelViewCatalogPageExpiringComposer extends MessageComposer {
    private final String name;
    private final int time;
    private final String image;

    public HotelViewCatalogPageExpiringComposer(String name, int time, String image) {
        this.name = name;
        this.time = time;
        this.image = image;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelViewCatalogPageExpiringComposer);
        this.response.appendString(this.name);
        this.response.appendInt(this.time);
        this.response.appendString(this.image);
        return this.response;
    }

    public String getName() {
        return name;
    }

    public int getTime() {
        return time;
    }

    public String getImage() {
        return image;
    }
}
