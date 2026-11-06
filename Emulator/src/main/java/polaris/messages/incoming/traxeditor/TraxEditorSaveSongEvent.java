package polaris.messages.incoming.traxeditor;

import polaris.Emulator;
import polaris.hotel.traxeditor.TraxEditorManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.traxeditor.TraxEditorErrorComposer;
import polaris.messages.outgoing.traxeditor.TraxEditorSongsComposer;

public class TraxEditorSaveSongEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        int songId = this.packet.readInt();
        String name = this.packet.readString();
        String data = this.packet.readString();

        TraxEditorManager manager = Emulator.getGameEnvironment().getTraxEditorManager();
        int result = manager.saveSong(user, songId, name, data);

        if (result != 0) {
            this.client.sendResponse(new TraxEditorErrorComposer(result));
            return;
        }

        this.client.sendResponse(new TraxEditorSongsComposer(
                manager.getMaxSongs(),
                manager.getCostCurrency(),
                manager.getCostAmount(),
                manager.getSongs(user.getUserInfo().getId())));
    }
}
