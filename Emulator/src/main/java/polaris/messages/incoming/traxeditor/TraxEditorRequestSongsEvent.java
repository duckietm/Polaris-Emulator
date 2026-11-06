package polaris.messages.incoming.traxeditor;

import polaris.Emulator;
import polaris.hotel.traxeditor.TraxEditorManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.traxeditor.TraxEditorSongsComposer;

public class TraxEditorRequestSongsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        TraxEditorManager manager = Emulator.getGameEnvironment().getTraxEditorManager();

        this.client.sendResponse(new TraxEditorSongsComposer(
                manager.getMaxSongs(),
                manager.getCostCurrency(),
                manager.getCostAmount(),
                manager.getSongs(user.getUserInfo().getId())));
    }
}
