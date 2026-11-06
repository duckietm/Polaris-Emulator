package polaris.messages.incoming.mentions;

import polaris.Emulator;
import polaris.hotel.mentions.MentionManager;
import polaris.hotel.mentions.MentionPage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.mentions.MentionsPageComposer;

/** The next page of mentions: those older than the id the client already has at the bottom. */
public class RequestMentionsPageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getUser() == null) return;

        int beforeId = this.packet.readInt();

        if (beforeId <= 0) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        MentionManager manager = Emulator.getGameEnvironment().getMentionManager();

        if (!manager.tryAcquireRequestPage(userId)) {
            return;
        }

        MentionPage page = manager.getMentionsPage(userId, manager.pageSize(), beforeId);
        this.client.sendResponse(new MentionsPageComposer(page));
    }
}
