package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Base for Phase-2 wired storage chests (config-based v1). A chest is a config-holder furni (it opens
 * a dialog via the {@link InteractionWiredExtra} channel, but is never applied as a stack add-on — the
 * engine only matches specific add-on classes) whose contents live as JSON in its own
 * {@code items.wired_data} ({@link ChestStorage}). The give effects + chest conditions read/mutate the
 * contents through {@link #getContents()} and persist with {@link #persistContents()}.
 */
public abstract class InteractionWiredChest extends InteractionWiredExtra {
    protected ChestStorage contents = new ChestStorage();

    protected InteractionWiredChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredChest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return this.contents.toJson();
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.contents = ChestStorage.fromJson(set.getString("wired_data"));
        resolveLegacySpriteIds(this.contents);
    }

    /**
     * Payloads persisted before the sprite id was stored per row only carry the internal base item
     * id. The client needs the furnidata sprite id to render icons/names, so backfill it here — the
     * next persist writes it out permanently.
     */
    private static void resolveLegacySpriteIds(ChestStorage contents) {
        if (Emulator.getGameEnvironment() == null || Emulator.getGameEnvironment().getItemManager() == null) return;

        for (ChestFurniStoredItem stored : contents.furniItems()) {
            if (stored.spriteId > 0) continue;

            Item base = Emulator.getGameEnvironment().getItemManager().getItem(stored.baseItemId);
            if (base != null) stored.spriteId = base.getSpriteId();
        }
    }

    @Override
    public void onPickUp() {
        this.contents = new ChestStorage();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public ChestStorage getContents() {
        return this.contents;
    }

    /** Schedule a save of the (mutated) contents to items.wired_data via {@code InteractionWired.run()}. */
    public void persistContents() {
        this.needsUpdate(true);
    }
}
