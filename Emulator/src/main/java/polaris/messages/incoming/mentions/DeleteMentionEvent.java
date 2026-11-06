package polaris.messages.incoming.mentions;

import polaris.Emulator;
import polaris.hotel.mentions.MentionManager;
import polaris.messages.incoming.MessageHandler;

public class DeleteMentionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getUser() == null) return;

        int userId = this.client.getUser().getUserInfo().getId();
        int mentionId = this.packet.readInt();

        if (mentionId <= 0) {
            return;
        }

        MentionManager manager = Emulator.getGameEnvironment().getMentionManager();

        manager.delete(userId, mentionId);
    }
}
