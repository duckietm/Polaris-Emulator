package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Official Chest Item Type Scanner (18, {@code wf_xtra_scan_chest_furni_by_type}, AIR {@code §_-i2B§}): when its stack
 * runs, counts the furni in the chests of furni slot 1 whose type is one of the furni of slot 0, and stores the count
 * in a context variable for that run ({@code wiredfurni.params.chest_item_type_scanner.info}).
 *
 * <p>Ints {@code [mode]}: 0 scans everything in the chests, 1 only the items the chests display on their lid. The
 * variable is the definition's first variable id; slot 0 picks are the first pick list, slot 1 picks the second.
 * A chest that does not answer wired is not read, as for the chest conditions.
 */
public class WiredExtraChestItemTypeScanner extends InteractionWiredExtra
        implements WiredOfficialSources, WiredOfficialVariableIds, WiredOfficialSecondaryPicks {
    public static final int CODE = 18;
    public static final int MODE_ALL = 0;
    public static final int MODE_DISPLAYED = 1;
    public static final int DEFAULT_TYPE_SOURCE = WiredSourceUtil.SOURCE_SELECTED;
    public static final int DEFAULT_CHEST_SOURCE = WiredVariableOperand.SOURCE_SECONDARY_SELECTED;

    private static final String REQUIRE_FURNI_CHESTS = "wiredfurni.error.require_furni_chests";

    private int mode = MODE_ALL;
    private int variableItemId = 0;
    private int typeSource = DEFAULT_TYPE_SOURCE;
    private int chestSource = DEFAULT_CHEST_SOURCE;
    private final List<Integer> typeItemIds = new ArrayList<>();
    private final List<Integer> chestIds = new ArrayList<>();

    public WiredExtraChestItemTypeScanner(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraChestItemTypeScanner(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    /** Writes this run's count into the chosen context variable; false when there is no variable to write. */
    public boolean scan(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableItemId <= 0) return false;

        Room room = ctx.room();
        Set<Integer> types = new HashSet<>();
        for (HabboItem item : this.resolve(ctx, room, this.typeSource, this.typeItemIds)) {
            if (item.getBaseItem() != null) types.add(item.getBaseItem().getId());
        }

        List<InteractionWiredChest> chests = new ArrayList<>();
        for (HabboItem item : this.resolve(ctx, room, this.chestSource, this.chestIds)) {
            if (item instanceof InteractionWiredChest chest && !chests.contains(chest)) chests.add(chest);
        }

        return WiredContextVariableSupport.assignVariable(
                ctx, room, this.variableItemId, count(chests, types, this.mode), true);
    }

    /**
     * How many stored furni of {@code types} the chests hold, or show when {@code mode} is {@link #MODE_DISPLAYED}.
     * Only chests that answer wired are read; an item a pending transaction holds is not available.
     */
    public static int count(Collection<? extends InteractionWiredChest> chests, Set<Integer> types, int mode) {
        if (chests == null || types == null || types.isEmpty()) return 0;

        int total = 0;
        for (InteractionWiredChest chest : chests) {
            if (chest == null || !chest.answersWired()) continue;

            List<ChestFurniStoredItem> items = mode == MODE_DISPLAYED
                    ? chest.displayedFurni()
                    : chest.getContents().furniItems();
            for (ChestFurniStoredItem item : items) {
                if (item != null && item.lockState == 0 && types.contains(item.baseItemId)) total++;
            }
        }
        return total;
    }

    private List<HabboItem> resolve(WiredContext ctx, Room room, int source, List<Integer> picks) {
        List<HabboItem> picked = new ArrayList<>();
        if (source == WiredSourceUtil.SOURCE_SELECTED || source == WiredVariableOperand.SOURCE_SECONDARY_SELECTED) {
            for (Integer id : picks) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) picked.add(item);
            }
            return WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTED, picked);
        }
        return WiredSourceUtil.resolveItems(ctx, source, picked);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = settings.getRoom();
        if (room == null && WiredPlatform.gameEnvironment() != null) {
            room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        }
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        String[] variableIds = settings.getVariableIds();
        int nextVariable = parseVariableId(variableIds.length > 0 ? variableIds[0] : "");
        if (nextVariable <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }
        WiredExtraContextVariable definition = WiredContextVariableSupport.getDefinition(room, nextVariable);
        if (definition == null || definition.isArray() || !definition.hasValue()) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        int[] chestPicks = settings.getFurniIds2() != null ? settings.getFurniIds2() : new int[0];
        for (int id : chestPicks) {
            HabboItem item = room.getHabboItem(id);
            // A pick the room no longer holds is left out; anything else must be a furni chest.
            if (item != null && !(item instanceof InteractionWiredChestFurni)) {
                throw new WiredSaveException(REQUIRE_FURNI_CHESTS);
            }
        }

        int[] params = settings.getIntParams();
        this.mode = normalizeMode(params.length > 0 ? params[0] : MODE_ALL);
        this.variableItemId = nextVariable;
        this.typeSource = normalizeSource(settings.furniSource(0, DEFAULT_TYPE_SOURCE), DEFAULT_TYPE_SOURCE);
        this.chestSource = normalizeSource(settings.furniSource(1, DEFAULT_CHEST_SOURCE), DEFAULT_CHEST_SOURCE);
        copyPicks(this.typeItemIds, settings.getFurniIds());
        copyPicks(this.chestIds, chestPicks);
        return true;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData();
        data.mode = this.mode;
        data.variableItemId = this.variableItemId;
        data.furniSource = this.typeSource;
        data.furniSource2 = this.chestSource;
        data.typeItemIds = new ArrayList<>(this.typeItemIds);
        data.chestIds = new ArrayList<>(this.chestIds);
        return WiredManager.getGson().toJson(data);
    }

    /**
     * Rows of the custom selector this box replaced ({@code filterExisting, invert, chestIds, delay}) keep their
     * chests as the chest picks and open on the defaults otherwise; unreadable rows open on the defaults.
     */
    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;

        this.mode = normalizeMode(data.mode);
        this.variableItemId = Math.max(0, data.variableItemId);
        if (data.furniSource != null) this.typeSource = normalizeSource(data.furniSource, DEFAULT_TYPE_SOURCE);
        if (data.furniSource2 != null) this.chestSource = normalizeSource(data.furniSource2, DEFAULT_CHEST_SOURCE);
        if (data.typeItemIds != null) copyPicks(this.typeItemIds, data.typeItemIds);
        if (data.chestIds != null) copyPicks(this.chestIds, data.chestIds);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.typeItemIds.size());
        for (Integer id : this.typeItemIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.mode);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.typeSource, this.chestSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public String[] officialVariableIds() {
        return this.variableItemId > 0
                ? new String[] {WiredVariableIds.toWire(WiredVariableTargets.OFFICIAL_CONTEXT, this.variableItemId)}
                : new String[0];
    }

    @Override
    public int[] officialFurniIds2() {
        return this.chestIds.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void onPickUp() {
        this.mode = MODE_ALL;
        this.variableItemId = 0;
        this.typeSource = DEFAULT_TYPE_SOURCE;
        this.chestSource = DEFAULT_CHEST_SOURCE;
        this.typeItemIds.clear();
        this.chestIds.clear();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getMode() {
        return this.mode;
    }

    public int getVariableItemId() {
        return this.variableItemId;
    }

    public List<Integer> getTypeItemIds() {
        return List.copyOf(this.typeItemIds);
    }

    public List<Integer> getChestIds() {
        return List.copyOf(this.chestIds);
    }

    private static void copyPicks(List<Integer> target, int[] ids) {
        List<Integer> values = new ArrayList<>();
        if (ids != null) for (int id : ids) values.add(id);
        copyPicks(target, values);
    }

    /** Distinct positive ids, no more than a box may pick. */
    private static void copyPicks(List<Integer> target, Collection<Integer> ids) {
        Set<Integer> distinct = new LinkedHashSet<>();
        for (Integer id : ids) {
            if (id == null || id <= 0) continue;
            if (distinct.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) break;
            distinct.add(id);
        }
        target.clear();
        target.addAll(distinct);
    }

    private static int normalizeMode(int mode) {
        return mode == MODE_DISPLAYED ? MODE_DISPLAYED : MODE_ALL;
    }

    private static int normalizeSource(int source, int fallback) {
        return switch (source) {
            case WiredSourceUtil.SOURCE_TRIGGER,
                    WiredSourceUtil.SOURCE_SELECTED,
                    WiredVariableOperand.SOURCE_SECONDARY_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL -> source;
            default -> fallback;
        };
    }

    private static int parseVariableId(String text) {
        // The variable list's id (ctx:12), or custom:12 / a bare box id from an older client.
        return WiredVariableIds.boxIdOf(text);
    }

    static class JsonData {
        int mode;
        int variableItemId;
        Integer furniSource;
        Integer furniSource2;
        List<Integer> typeItemIds;
        List<Integer> chestIds;
    }
}
