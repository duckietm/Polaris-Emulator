package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** ClaimRewardTrackPrize (1111): claim a prize of a reward track. */
public class ClaimRewardTrackPrizeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String trackId = this.packet.readString();
        String rewardId = this.packet.readString();
        Emulator.getGameEnvironment().getRewardTrackManager().claim(this.client.getUser(), trackId, rewardId);
    }
}
