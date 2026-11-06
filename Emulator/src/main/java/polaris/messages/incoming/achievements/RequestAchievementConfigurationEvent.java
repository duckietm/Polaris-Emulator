package polaris.messages.incoming.achievements;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryAchievementsComposer;

public class RequestAchievementConfigurationEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new InventoryAchievementsComposer());
    }
}
