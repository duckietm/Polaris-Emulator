package polaris.messages.outgoing.hotelview;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelViewBadgeButtonConfigComposer extends MessageComposer {
    private final String badge;
    private final boolean enabled;

    public HotelViewBadgeButtonConfigComposer(String badge, boolean enabled) {
        this.badge = badge;
        this.enabled = enabled;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelViewBadgeButtonConfigComposer);
        this.response.appendString(this.badge);
        this.response.appendBoolean(this.enabled);
        return this.response;
    }

    public String getBadge() {
        return badge;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
