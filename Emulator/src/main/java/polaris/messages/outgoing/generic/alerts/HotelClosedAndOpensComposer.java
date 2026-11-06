package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelClosedAndOpensComposer extends MessageComposer {
    private final int hour;
    private final int minute;

    public HotelClosedAndOpensComposer(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelClosedAndOpensComposer);
        this.response.appendInt(this.hour);
        this.response.appendInt(this.minute);
        return this.response;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }
}
