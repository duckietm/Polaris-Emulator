package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** PurchaseRewardTrackPremium (3022): buy the premium tier of a reward track. */
public class PurchaseRewardTrackPremiumEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String trackId = this.packet.readString();
        Emulator.getGameEnvironment().getRewardTrackManager().purchasePremium(this.client.getUser(), trackId);
    }
}
