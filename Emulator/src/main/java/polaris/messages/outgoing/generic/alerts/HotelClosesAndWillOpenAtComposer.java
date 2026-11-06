package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelClosesAndWillOpenAtComposer extends MessageComposer {
    private final int hour;
    private final int minute;
    private final boolean disconnected;

    public HotelClosesAndWillOpenAtComposer(int hour, int minute, boolean disconnected) {
        this.hour = hour;
        this.minute = minute;
        this.disconnected = disconnected;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelClosesAndWillOpenAtComposer);
        this.response.appendInt(this.hour);
        this.response.appendInt(this.minute);
        this.response.appendBoolean(this.disconnected);
        return this.response;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public boolean isDisconnected() {
        return disconnected;
    }
}
