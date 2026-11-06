package polaris.messages.incoming.mentions;

import polaris.Emulator;
import polaris.hotel.mentions.MentionManager;
import polaris.hotel.mentions.MentionPage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.mentions.MentionsListComposer;

public class RequestMentionsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getUser() == null) return;

        int userId = this.client.getUser().getUserInfo().getId();

        MentionManager manager = Emulator.getGameEnvironment().getMentionManager();

        if (!manager.tryAcquireRequestList(userId)) {
            return;
        }

        MentionPage page = manager.getMentionsPage(userId, manager.pageSize(), 0);
        this.client.sendResponse(new MentionsListComposer(page.mentions(), page.hasMore()));
    }
}
