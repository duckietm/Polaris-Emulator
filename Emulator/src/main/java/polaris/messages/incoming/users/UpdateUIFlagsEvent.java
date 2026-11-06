package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;

public class UpdateUIFlagsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int flags = UserInputGuard.sanitizeUiFlags(this.packet.readInt());

        this.client.getUser().getUserStats().uiFlags = flags;
        this.client.getUser().getUserStats().run();
    }
}
