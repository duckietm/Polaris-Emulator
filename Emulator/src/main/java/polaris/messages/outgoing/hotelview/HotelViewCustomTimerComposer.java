package polaris.messages.outgoing.hotelview;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.UnsupportedOutgoing;

public class HotelViewCustomTimerComposer extends MessageComposer {
    private final String name;
    private final int seconds;

    public HotelViewCustomTimerComposer(String name, int seconds) {
        this.name = name;
        this.seconds = seconds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(UnsupportedOutgoing.HotelViewCustomTimerComposer);
        this.response.appendString(this.name); // Send by the client.
        this.response.appendInt(this.seconds);
        return this.response;
    }

    public String getName() {
        return name;
    }

    public int getSeconds() {
        return seconds;
    }
}
