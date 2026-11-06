package polaris.messages.incoming.soundboard;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.soundboard.SoundboardCatalogRepository;
import polaris.hotel.soundboard.SoundboardCatalogResult;
import polaris.hotel.soundboard.SoundboardManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.soundboard.SoundboardCatalogResultComposer;

public class SoundboardCatalogReorderEvent extends MessageHandler {
    private static final int MAX_REORDER_SIZE = SoundboardCatalogRepository.MAX_CATALOG_SIZE;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (!SoundboardManagementAccess.canManage(user)) {
            this.sendResult(SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.FORBIDDEN));
            return;
        }

        int count = this.packet.readInt();
        if (!isCountAllowed(count)) {
            this.sendResult(SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_ORDER));
            return;
        }

        List<Integer> orderedIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            orderedIds.add(this.packet.readInt());
        }

        GameEnvironment environment = Emulator.getGameEnvironment();
        SoundboardManager manager = environment.getSoundboardManager();
        SoundboardCatalogResult result = manager.reorder(user.getUserInfo().getId(), orderedIds);
        this.sendResult(result);
        if (result.successful()) {
            SoundboardSettingsSender.sendToActiveRooms(manager, environment.getRoomManager());
        }
    }

    static boolean isCountAllowed(int count) {
        return count >= 0 && count <= MAX_REORDER_SIZE;
    }

    private void sendResult(SoundboardCatalogResult result) {
        this.client.sendResponse(
                new SoundboardCatalogResultComposer(SoundboardCatalogResultComposer.Operation.REORDER, result)
                        .compose());
    }
}
