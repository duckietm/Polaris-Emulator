package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.soundboard.SoundboardCatalogCommand;
import com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardCatalogResultComposer;

public class SoundboardCatalogUpsertEvent extends MessageHandler {

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

        int id = this.packet.readInt();
        String name = this.packet.readString();
        String url = this.packet.readString();
        int minRank = this.packet.readInt();
        boolean enabled = this.packet.readBoolean();

        // Trailing field: a client that predates the asset-backed soundboard
        // simply does not send it, and keeps addressing pads by URL.
        String classname = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";

        // Trailing again, after the classname: the pad's own cooldown in seconds. Without it an
        // update keeps the stored value.
        int cooldownSeconds =
                this.packet.bytesAvailable() > 0 ? this.packet.readInt() : SoundboardCatalogCommand.KEEP_COOLDOWN;

        SoundboardCatalogCommand command =
                new SoundboardCatalogCommand(id, name, classname, url, minRank, enabled, cooldownSeconds);
        GameEnvironment environment = Emulator.getGameEnvironment();
        SoundboardManager manager = environment.getSoundboardManager();
        SoundboardCatalogResult result = manager.upsert(habbo.getHabboInfo().getId(), command);
        this.sendResult(result);
        if (result.successful()) {
            SoundboardSettingsSender.sendToActiveRooms(manager, environment.getRoomManager());
        }
    }

    private void sendResult(SoundboardCatalogResult result) {
        this.client.sendResponse(
                new SoundboardCatalogResultComposer(SoundboardCatalogResultComposer.Operation.UPSERT, result)
                        .compose());
    }
}
