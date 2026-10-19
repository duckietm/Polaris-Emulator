package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Furni opacity is" ({@code wf_cnd_furni_opacity_is}): the opacity the room currently shows for
 * the resolved furni (0 invisible, 100 solid), compared to a value. Two ints: opacity, comparison
 * ({@link WiredComparison} codes); the furni in the official source slot, plus the furni selection. Every resolved furni has
 * to compare as asked. The opacity read is the one the triggering user sees, since a box can give
 * one user a private opacity. The negative box is {@link WiredConditionNotFurniOpacityIs}.
 */
public class WiredConditionFurniOpacityIs extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.FURNI_OPACITY;
    public static final int MIN_OPACITY = 0;
    public static final int MAX_OPACITY = 100;

    private int opacity = MAX_OPACITY;
    private int comparison = WiredComparison.EQUAL;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private final List<Integer> furniIds = new ArrayList<>();

    public WiredConditionFurniOpacityIs(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionFurniOpacityIs(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx == null ? null : ctx.room();
        if (room == null || room.getWiredRuntime() == null) return false;

        List<HabboItem> items = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedItems(room));
        if (items.isEmpty()) return false;

        Set<Integer> ids = new LinkedHashSet<>();
        for (HabboItem item : items) ids.add(item.getId());
        List<WiredOpacityState> states =
                room.getWiredRuntime().effectiveOpacity(viewerId(ctx, room), new ArrayList<>(ids));
        if (states == null) return false;

        boolean seen = false;
        for (WiredOpacityState state : states) {
            if (state == null || !ids.contains(state.itemId())) continue;
            if (!this.matchesItem(state.opacity())) return false;
            seen = true;
        }
        return seen;
    }

    /** Whether one furni's opacity satisfies the box; the negative box turns this around. */
    protected boolean matchesItem(int shown) {
        return WiredComparison.compare(shown, this.opacity, this.comparison);
    }

    private static int viewerId(WiredContext ctx, Room room) {
        List<RoomUnit> units = WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER);
        if (units.isEmpty() || units.get(0) == null) return 0;
        Habbo habbo = room.getHabbo(units.get(0));
        return (habbo == null || habbo.getHabboInfo() == null)
                ? 0
                : habbo.getHabboInfo().getId();
    }

    private List<HabboItem> selectedItems(Room room) {
        List<HabboItem> items = new ArrayList<>();
        for (Integer id : this.furniIds) {
            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }
        return items;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.opacity = normalizeOpacity(param(params, 0, MAX_OPACITY));
        this.comparison = WiredComparison.normalize(param(params, 1, WiredComparison.EQUAL));
        // The furni come from the official source slot; the ints keep only the opacity and comparison.
        this.furniSource = settings.getFurniSourceTypes().length > 0
                ? settings.getFurniSourceTypes()[0]
                : WiredSourceUtil.SOURCE_SELECTED;
        this.furniIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.furniIds.add(id);
        }
        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.opacity, this.comparison, this.furniSource, new ArrayList<>(this.furniIds)));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.furniIds.size());
        for (Integer id : this.furniIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.opacity);
        message.appendInt(this.comparison);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.opacity = normalizeOpacity(data.opacity);
        this.comparison = WiredComparison.normalize(data.comparison);
        this.furniSource = data.furniSource;
        if (data.furniIds != null) this.furniIds.addAll(data.furniIds);
    }

    @Override
    public void onPickUp() {
        this.opacity = MAX_OPACITY;
        this.comparison = WiredComparison.EQUAL;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.furniIds.clear();
        this.setExtradata("");
    }

    static int normalizeOpacity(int value) {
        return Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, value));
    }

    private static int param(int[] params, int index, int fallback) {
        return (params != null && params.length > index) ? params[index] : fallback;
    }

    static class JsonData {
        int opacity;
        int comparison;
        int furniSource;
        List<Integer> furniIds;

        JsonData(int opacity, int comparison, int furniSource, List<Integer> furniIds) {
            this.opacity = opacity;
            this.comparison = comparison;
            this.furniSource = furniSource;
            this.furniIds = furniIds;
        }
    }
}
