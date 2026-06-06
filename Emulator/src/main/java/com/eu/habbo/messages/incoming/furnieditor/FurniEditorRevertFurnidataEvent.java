package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnidataEntry;
import com.eu.habbo.habbohotel.items.FurnidataLock;
import com.eu.habbo.habbohotel.items.FurnidataWriter;
import com.eu.habbo.habbohotel.items.FurnitureTextProvider;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
        Habbo habbo = this.client.getHabbo();

        // 1. Permission check
        if (!habbo.hasPermission(Permission.ACC_CATALOGFURNI)) {
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
        FurnitureTextProvider provider =
            Emulator.getGameEnvironment().getFurnitureTextProvider();

        if (provider == null || provider.getSource() == null) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Furnidata source not configured"));
            return;
        }

        int adminId = habbo.getHabboInfo().getId();

        // 5. Revert + reindex + broadcast under the shared lock
        boolean reverted;
        List<FurnidataEntry> delta;

        FurnidataLock.LOCK.lock();
        try {
            FurnidataWriter writer = new FurnidataWriter(
                provider.getSource(),
                provider.isSourceDirectory(),
                provider.getMaxBytes(),
                3 /* backupKeep */
            );
            reverted = writer.revertLastBackup();
            if (!reverted) {
                this.client.sendResponse(new FurniEditorResultComposer(false, "No backup found to revert"));
                return;
            }

            delta = provider.reindexFromSource();

            if (!delta.isEmpty()) {
                int deltaCap = Integer.parseInt(
                    Emulator.getConfig().getValue("items.furnidata.delta.cap", "500"));
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
            "REVERT_FURNIDATA",
            "", // previous state unknown at this point
            "",
            "",
            ""
        );

        // 7. Respond success
        this.client.sendResponse(new FurniEditorResultComposer(true, "Furnidata reverted", itemId));
        LOGGER.info("FurniEditorRevertFurnidataEvent: admin {} reverted furnidata for classname '{}' (item {})",
            adminId, classnameForLog, itemId);
    }

    private static void broadcastToAll(FurnitureDataReloadComposer composer) {
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(composer);
            }
        }
    }
}
