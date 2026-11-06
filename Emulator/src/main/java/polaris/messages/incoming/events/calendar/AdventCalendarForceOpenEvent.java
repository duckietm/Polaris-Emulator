package polaris.messages.incoming.events.calendar;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

public class AdventCalendarForceOpenEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String campaignName = this.packet.readString();
        int day = this.packet.readInt();

        Emulator.getGameEnvironment()
                .getCalendarManager()
                .claimCalendarReward(this.client.getUser(), campaignName, day, true);
    }
}
