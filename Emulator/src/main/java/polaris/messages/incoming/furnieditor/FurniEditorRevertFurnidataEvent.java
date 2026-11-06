package polaris.messages.incoming.furnieditor;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.FurnidataEntry;
import polaris.hotel.items.FurnidataLock;
import polaris.hotel.items.FurnidataWriter;
import polaris.hotel.items.FurnitureTextProvider;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.furnieditor.FurniEditorResultComposer;
import polaris.messages.outgoing.furniture.FurnitureDataReloadComposer;

/**
 * Incoming handler 10048 — admin reverts a furni's furnidata to the last rotating backup.
 *
 * Flow: permission check → read item_id → resolve classname → under FurnidataLock:
 * FurnidataWriter.revertLastBackup → FurnitureTextProvider.reindexFromSource →
 * broadcast FurnitureDataReloadComposer (10047) → audit log → respond.
 */
public class FurniEditorRevertFurnidataEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurniEditorRevertFurnidataEvent.class);

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();

        // 1. Permission check
        if (!user.hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        // 2. Read packet
        int itemId = this.packet.readInt();

        if (itemId <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid item ID"));
            return;
        }

        // 3. Resolve classname from item_id (reuse static helper from update handler)
        String classname = FurniEditorUpdateFurnidataEvent.classnameForItem(itemId);
        String classnameForLog = (classname != null) ? classname : "?";

        // 4. Verify provider is configured
        FurnitureTextProvider provider = Emulator.getGameEnvironment().getFurnitureTextProvider();

        if (provider == null || provider.getSource() == null) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Furnidata source not configured"));
            return;
        }
        if (provider.isSourceReadOnly()) {
            this.client.sendResponse(new FurniEditorResultComposer(
                    false, "Furnidata is downloaded from a URL (items.furnidata.path): edit it on the web server"));
            return;
        }

        int adminId = user.getUserInfo().getId();

        // 5. Revert + reindex + broadcast under the shared lock
        boolean reverted;
        List<FurnidataEntry> delta;

        FurnidataLock.LOCK.lock();
        try {
            FurnidataWriter writer = new FurnidataWriter(
                    provider.getSource(), provider.isSourceDirectory(), provider.getMaxBytes(), 3 /* backupKeep */);
            reverted = writer.revertLastBackup();
            if (!reverted) {
                this.client.sendResponse(new FurniEditorResultComposer(false, "No backup found to revert"));
                return;
            }

            delta = provider.reindexFromSource();

            if (!delta.isEmpty()) {
                int deltaCap = Integer.parseInt(Emulator.getConfig().getValue("items.furnidata.delta.cap", "500"));
                FurnitureDataReloadComposer composer = (delta.size() > deltaCap)
                        ? new FurnitureDataReloadComposer(FurnitureDataReloadComposer.MODE_RELOAD_HINT, List.of())
                        : new FurnitureDataReloadComposer(FurnitureDataReloadComposer.MODE_DELTA, delta);
                broadcastToAll(composer);
            }
        } finally {
            FurnidataLock.LOCK.unlock();
        }

        // 6. Audit log (outside lock — DB write, not latency-sensitive)
        FurnidataAuditLog.record(
                adminId,
                classnameForLog,
                "revert",
                "", // previous state unknown at this point
                "",
                "",
                "");

        // 7. Respond success
        this.client.sendResponse(new FurniEditorResultComposer(true, "Furnidata reverted", itemId));
        LOGGER.info(
                "FurniEditorRevertFurnidataEvent: admin {} reverted furnidata for classname '{}' (item {})",
                adminId,
                classnameForLog,
                itemId);
    }

    private static void broadcastToAll(FurnitureDataReloadComposer composer) {
        for (User user :
                Emulator.getGameEnvironment().getUserManager().getOnlineUsers().values()) {
            if (user.getClient() != null) {
                user.getClient().sendResponse(composer);
            }
        }
    }
}
