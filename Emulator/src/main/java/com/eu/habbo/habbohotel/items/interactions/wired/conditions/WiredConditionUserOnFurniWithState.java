package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Passes when a resolved user stands on one of the selected furni AND that furni's
 * {@link HabboItem#getExtradata() extradata state} equals a required value.
 *
 * <p>This is a specialisation of {@link WiredConditionTriggerOnFurni}: it reuses the same furni
 * picker, {@code userSource}, and {@code quantifier} machinery (and therefore the same int-param
 * layout {@code [furniSource, userSource, quantifier]} and furni-id selection), but additionally
 * carries a single {@code requiredState} string. When that string is non-empty, a user only counts
 * as "on the furni" if the furni under their feet currently reports that exact extradata state. An
 * empty {@code requiredState} degrades gracefully to the plain on-furni behaviour.</p>
 *
 * <p>The state-match idiom mirrors {@link WiredConditionMatchStatePosition#matchesSetting}
 * ({@code item.getExtradata().equals(requiredState)}). Serialization differs from the parent only in
 * the string slot: the parent appends {@code ""}; here we append the {@code requiredState}. The int
 * layout, furni-selection flag, and trailing pad/code are byte-for-byte identical, so it reuses the
 * dedicated new client dialog {@code WiredConditionlayout.USER_ON_FURNI_WITH_STATE} (45).</p>
 */
public class WiredConditionUserOnFurniWithState extends WiredConditionTriggerOnFurni {

    public static final WiredConditionType type = WiredConditionType.USER_ON_FURNI_WITH_STATE;

    private static final int MAX_STATE_LENGTH = 256;

    private String requiredState = "";

    public WiredConditionUserOnFurniWithState(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserOnFurniWithState(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        this.refresh();

        List<RoomUnit> userTargets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (userTargets.isEmpty())
            return false;

        List<HabboItem> itemTargets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (itemTargets.isEmpty())
            return false;

        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return this.isAnyUserOnFurniWithState(userTargets, itemTargets, ctx.room());
        }

        return this.areAllUsersOnFurniWithState(userTargets, itemTargets, ctx.room());
    }

    private boolean isAnyUserOnFurniWithState(Collection<RoomUnit> users, Collection<HabboItem> items, Room room) {
        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) continue;
            if (this.matchingItemUnderUser(roomUnit, items, room)) {
                return true;
            }
        }
        return false;
    }

    private boolean areAllUsersOnFurniWithState(Collection<RoomUnit> users, Collection<HabboItem> items, Room room) {
        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) {
                return false;
            }
            if (!this.matchingItemUnderUser(roomUnit, items, room)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchingItemUnderUser(RoomUnit roomUnit, Collection<HabboItem> items, Room room) {
        Set<HabboItem> itemsAtUser = room.getItemsAt(roomUnit.getCurrentLocation());
        if (itemsAtUser == null) {
            return false;
        }

        for (HabboItem item : items) {
            if (item != null && itemsAtUser.contains(item) && this.matchesRequiredState(item)) {
                return true;
            }
        }

        return false;
    }

    boolean matchesRequiredState(HabboItem item) {
        if (this.requiredState == null || this.requiredState.isEmpty()) {
            return true;
        }

        if (item == null) {
            return false;
        }

        String extradata = item.getExtradata();
        return extradata != null && extradata.equals(this.requiredState);
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(
                this.items.stream().map(HabboItem::getId).toList(),
                this.furniSource,
                this.userSource,
                this.getQuantifier(),
                this.requiredState
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data == null) {
                return;
            }

            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);
            this.userSource = WiredFurniConditionInputGuard.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);
            this.requiredState = this.normalizeRequiredState(data.requiredState);

            for (int id : WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                HabboItem item = room.getHabboItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }

            this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, !this.items.isEmpty());
        }
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.requiredState = "";
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.requiredState == null ? "" : this.requiredState);
        message.appendInt(3);
        message.appendInt(this.furniSource);
        message.appendInt(this.userSource);
        message.appendInt(this.getQuantifier());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        int[] params = settings.getIntParams();
        this.furniSource = (params.length > 0) ? WiredFurniConditionInputGuard.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = (params.length > 1) ? WiredFurniConditionInputGuard.normalizeUserSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2]) : QUANTIFIER_ALL;
        this.requiredState = this.normalizeRequiredState(settings.getStringParam());

        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, count > 0);

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room != null) {
                for (int i = 0; i < count; i++) {
                    HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        }

        return true;
    }

    String normalizeRequiredState(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.length() > MAX_STATE_LENGTH) {
            return trimmed.substring(0, MAX_STATE_LENGTH);
        }

        return trimmed;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    static class JsonData {
        List<Integer> itemIds;
        int furniSource;
        int userSource;
        int quantifier;
        String requiredState;

        public JsonData(List<Integer> itemIds, int furniSource, int userSource, int quantifier, String requiredState) {
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.userSource = userSource;
            this.quantifier = quantifier;
            this.requiredState = requiredState;
        }
    }
}
