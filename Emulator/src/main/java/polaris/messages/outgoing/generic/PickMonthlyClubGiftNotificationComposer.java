package polaris.messages.outgoing.generic;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PickMonthlyClubGiftNotificationComposer extends MessageComposer {
    private final int count;

    public PickMonthlyClubGiftNotificationComposer(int count) {
        this.count = count;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PickMonthlyClubGiftNotificationComposer);
        this.response.appendInt(this.count);
        return this.response;
    }

    public int getCount() {
        return count;
    }
}
