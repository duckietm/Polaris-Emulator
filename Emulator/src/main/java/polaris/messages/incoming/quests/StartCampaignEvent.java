package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** StartCampaign (1697): the tracker auto-starts the default campaign. */
public class StartCampaignEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String campaignCode = this.packet.readString();
        Emulator.getGameEnvironment().getQuestManager().startCampaign(this.client.getUser(), campaignCode);
    }
}
