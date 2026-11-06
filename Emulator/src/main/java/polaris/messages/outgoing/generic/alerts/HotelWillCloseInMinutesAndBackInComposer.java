package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelWillCloseInMinutesAndBackInComposer extends MessageComposer {
    private final int closeInMinutes;
    private final int reopenInMinutes;

    public HotelWillCloseInMinutesAndBackInComposer(int closeInMinutes, int reopenInMinutes) {
        this.closeInMinutes = closeInMinutes;
        this.reopenInMinutes = reopenInMinutes;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelWillCloseInMinutesAndBackInComposer);
        this.response.appendBoolean(true);
        this.response.appendInt(this.closeInMinutes);
        this.response.appendInt(this.reopenInMinutes);
        return this.response;
    }

    public int getCloseInMinutes() {
        return closeInMinutes;
    }

    public int getReopenInMinutes() {
        return reopenInMinutes;
    }
}
