package polaris.messages.outgoing.hotelview;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelViewSecondsUntilComposer extends MessageComposer {
    private final String dateString;
    private final int seconds;

    public HotelViewSecondsUntilComposer(String dateString, int seconds) {
        this.dateString = dateString;
        this.seconds = seconds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelViewSecondsUntilComposer);
        this.response.appendString(this.dateString);
        this.response.appendInt(this.seconds);

        return this.response;
    }

    public String getDateString() {
        return dateString;
    }

    public int getSeconds() {
        return seconds;
    }
}
