package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardCatalogResultComposer;
import java.util.ArrayList;
import java.util.List;

public class SoundboardCatalogReorderEvent extends MessageHandler {
    private static final int MAX_REORDER_SIZE = 500;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (!SoundboardManagementAccess.canManage(habbo)) {
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

        SoundboardManager manager = Emulator.getGameEnvironment().getSoundboardManager();
        SoundboardCatalogResult result = manager.reorder(habbo.getHabboInfo().getId(), orderedIds);
        this.sendResult(result);
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
