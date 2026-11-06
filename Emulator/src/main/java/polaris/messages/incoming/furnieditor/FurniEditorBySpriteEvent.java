package polaris.messages.incoming.furnieditor;

import polaris.Emulator;
import polaris.hotel.items.editor.FurniEditorRepository;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.furnieditor.FurniEditorResultComposer;

public class FurniEditorBySpriteEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        int spriteId = this.packet.readInt();

        if (spriteId <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid sprite ID"));
            return;
        }

        int itemId = new FurniEditorRepository(Emulator.getDatabase().getDataSource())
                .findItemIdBySprite(spriteId)
                .orElse(-1);

        if (itemId <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No item found with sprite_id: " + spriteId));
            return;
        }

        // Delegate to the detail response builder
        FurniEditorDetailEvent.sendDetailResponse(this.client, itemId);
    }
}
