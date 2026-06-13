package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnidataEntry;
import com.eu.habbo.habbohotel.items.FurnidataLock;
import com.eu.habbo.habbohotel.items.FurnidataWriter;
import com.eu.habbo.habbohotel.items.FurnitureTextProvider;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.FurnidataEntryBuilder;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Incoming handler 10046 — admin saves a furni name/description in the editor.
 *
 * Flow: permission check → rate-limit → resolve classname from item_id →
 * under FurnidataLock: FurnidataWriter.write → FurnitureTextProvider.reindexFromSource →
 * broadcast FurnitureDataReloadComposer (10047) → audit log → respond.
 */
public class FurniEditorUpdateFurnidataEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurniEditorUpdateFurnidataEvent.class);

    /** Rate-limit: min milliseconds between successive calls per admin user id. */
    private static final long RATE_LIMIT_MS = 1_000L;

    /** Per-admin last-call timestamp map. */
    private static final Map<Integer, Long> LAST_CALL = new ConcurrentHashMap<>();

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();

        // 1. Permission check
        if (!habbo.hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        // 2. Rate-limit per admin
        int adminId = habbo.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        Long last = LAST_CALL.get(adminId);
        if (last != null && (now - last) < RATE_LIMIT_MS) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Too many requests"));
            return;
        }
        LAST_CALL.put(adminId, now);

        // 3. Read packet
        int itemId = this.packet.readInt();
        JsonObject json;
        try {
            json = JsonParser.parseString(this.packet.readString()).getAsJsonObject();
        } catch (Exception e) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid JSON data"));
            return;
        }

        if (itemId <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid item ID"));
            return;
        }

        String name        = json.has("name")        ? json.get("name").getAsString()        : null;
        String description = json.has("description") ? json.get("description").getAsString() : null;

        if (name == null && description == null) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No name or description provided"));
            return;
        }

        // 4. Resolve classname from item_id
        String classname = classnameForItem(itemId);
        if (classname == null) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Item not found"));
            return;
        }

        // 5. Write + reindex + broadcast under the shared lock
        FurnitureTextProvider provider =
            Emulator.getGameEnvironment().getFurnitureTextProvider();

        if (provider == null || provider.getSource() == null) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Furnidata source not configured"));
            return;
        }

        // Capture old values (before write) for the audit log
        String oldName = provider.getName(classname);
        // description is not indexed in the provider — treat as empty string for audit
        String oldDesc = "";

        // FurnidataWriter.write() calls FurnitureTextProvider.sanitize() internally;
        // pass the raw values here and use them also for the audit log.
        String safeName = (name        != null) ? name        : "";
        String safeDesc = (description != null) ? description : "";

        boolean written;
        boolean created = false;
        List<FurnidataEntry> delta;

        FurnidataLock.LOCK.lock();
        try {
            FurnidataWriter writer = new FurnidataWriter(
                provider.getSource(),
                provider.isSourceDirectory(),
                provider.getMaxBytes(),
                3 /* backupKeep */
            );
            written = writer.write(classname, safeName, safeDesc);
            if (!written) {
                // Upsert: no furnidata entry for this classname yet → create a
                // complete one seeded from items_base (id = sprite id).
                Item item = Emulator.getGameEnvironment().getItemManager().getItem(itemId);
                if (item == null) {
                    this.client.sendResponse(new FurniEditorResultComposer(false, "Item not found"));
                    return;
                }
                String createTier = Emulator.getConfig().getValue("items.furnidata.create_tier", "custom");
                String entry = FurnidataEntryBuilder.build(
                    item,
                    FurnitureTextProvider.sanitize(safeName),
                    FurnitureTextProvider.sanitize(safeDesc));
                FurnidataWriter.CreateResult cr =
                    writer.create(item.getName(), item.getSpriteId(), item.getType(), entry, createTier);
                switch (cr) {
                    case CREATED:
                        created = true;
                        written = true;
                        break;
                    case ALREADY_EXISTS:
                        // entry already present (race / no-op edit) — apply the edit and treat as success
                        writer.write(classname, safeName, safeDesc);
                        written = true;
                        break;
                    case ID_COLLISION:
                        this.client.sendResponse(new FurniEditorResultComposer(false, "Sprite id already used by another classname"));
                        return;
                    default:
                        this.client.sendResponse(new FurniEditorResultComposer(false, "Failed to create furnidata entry"));
                        return;
                }
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

        // 5b. Auto-mirror the new display name into items_base.public_name (DB) so the
        //     server-side fallback (Item.getFullName) and the editor's read-only
        //     "Public Name" field stay in sync with the furnidata edit. Only when a
        //     name was actually supplied (description-only edits must not blank it).
        //     Kept outside FurnidataLock (independent DB write, like the audit log).
        if (name != null) {
            try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement st = c.prepareStatement("UPDATE items_base SET public_name = ? WHERE id = ?")) {
                st.setString(1, FurnitureTextProvider.sanitize(safeName));
                st.setInt(2, itemId);
                st.executeUpdate();
                // Refresh the in-memory Item cache (Item.fullName) in place — no restart needed.
                Emulator.getGameEnvironment().getItemManager().loadItems();
            } catch (Exception e) {
                LOGGER.warn("Failed to mirror furnidata name into items_base.public_name for item {}", itemId, e);
            }
        }

        // 6. Audit log (outside lock — DB write, not latency-sensitive)
        FurnidataAuditLog.record(
            adminId,
            classname,
            created ? "create" : "edit",
            oldName != null ? oldName : "",
            FurnitureTextProvider.sanitize(safeName),
            oldDesc,
            FurnitureTextProvider.sanitize(safeDesc)
        );

        // 7. Respond success
        this.client.sendResponse(new FurniEditorResultComposer(true, "Furnidata updated", itemId));
        LOGGER.info("FurniEditorUpdateFurnidataEvent: admin {} updated furnidata for classname '{}' (item {})",
            adminId, classname, itemId);
    }

    /**
     * Resolves the item_name (classname) from items_base for a given item id.
     * Kept static so FurniEditorRevertFurnidataEvent can reuse it.
     *
     * @return the classname string, or {@code null} if not found or on error.
     */
    public static String classnameForItem(int itemId) {
        try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement st = c.prepareStatement("SELECT item_name FROM items_base WHERE id = ?")) {
            st.setInt(1, itemId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getString("item_name");
            }
        } catch (Exception e) {
            LOGGER.warn("classnameForItem: failed to query items_base for id {}", itemId, e);
        }
        return null;
    }

    private static void broadcastToAll(FurnitureDataReloadComposer composer) {
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(composer);
            }
        }
    }
}
