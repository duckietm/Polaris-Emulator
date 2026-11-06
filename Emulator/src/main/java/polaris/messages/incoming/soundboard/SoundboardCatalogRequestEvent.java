package polaris.messages.incoming.soundboard;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.soundboard.SoundboardCatalogComposer;

public class SoundboardCatalogRequestEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (!SoundboardManagementAccess.canManage(user)) {
            return;
        }

        this.client.sendResponse(new SoundboardCatalogComposer(
                        Emulator.getGameEnvironment().getSoundboardManager().getCatalog())
                .compose());
    }
}
