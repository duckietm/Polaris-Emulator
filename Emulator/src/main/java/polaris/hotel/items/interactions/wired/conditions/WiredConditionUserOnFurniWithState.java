package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;

/**
 * Passes when a resolved user stands on one of the selected furni AND that furni's
 * {@link UserItem#getExtradata() extradata state} equals a required value.
 *
 * <p>This is a specialisation of {@link WiredConditionTriggerOnFurni}: it reuses the same furni
 * picker, {@code userSource}, and {@code quantifier} machinery (and therefore the same layout: no int
 * params, the quantifier in its slot, the furni and users in the official source slots, and furni-id selection), but additionally
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

    public WiredConditionUserOnFurniWithState(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        if (userTargets.isEmpty()) return WiredQuantifier.overEmpty(this.getQuantifier() != QUANTIFIER_ANY);

        List<UserItem> itemTargets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (itemTargets.isEmpty()) return false;

        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return this.isAnyUserOnFurniWithState(userTargets, itemTargets, ctx.room());
        }

        return this.areAllUsersOnFurniWithState(userTargets, itemTargets, ctx.room());
    }

    private boolean isAnyUserOnFurniWithState(Collection<RoomUnit> users, Collection<UserItem> items, Room room) {
        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) continue;
            if (this.matchingItemUnderUser(roomUnit, items, room)) {
                return true;
            }
        }
        return false;
    }

    private boolean areAllUsersOnFurniWithState(Collection<RoomUnit> users, Collection<UserItem> items, Room room) {
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

    private boolean matchingItemUnderUser(RoomUnit roomUnit, Collection<UserItem> items, Room room) {
        Set<UserItem> itemsAtUser = room.getItemsAt(roomUnit.getCurrentLocation());
        if (itemsAtUser == null) {
            return false;
        }

        for (UserItem item : items) {
            if (item != null && itemsAtUser.contains(item) && this.matchesRequiredState(item)) {
                return true;
            }
        }

        return false;
    }

    boolean matchesRequiredState(UserItem item) {
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
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.items.stream().map(UserItem::getId).toList(),
                        this.furniSource,
                        this.userSource,
                        this.getQuantifier(),
                        this.requiredState));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = parsePayload(wiredData);
            if (data == null) {
                return;
            }

            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);
            this.userSource = WiredFurniConditionInputGuard.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);
            this.requiredState = this.normalizeRequiredState(data.requiredState);

            for (int id :
                    WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                UserItem item = room.getUserItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }

            this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(
                    this.furniSource, !this.items.isEmpty());
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

        for (UserItem item : this.items) message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.requiredState == null ? "" : this.requiredState);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        // The parent's official layout: the furni and users in the official source slots, the quantifier in
        // its slot; the required state in the string.
        if (!super.saveData(settings)) return false;
        this.requiredState = this.normalizeRequiredState(settings.getStringParam());
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

    /**
     * A truncated document leaves Gson throwing EOFException, which is checked - so it escapes a
     * RuntimeException catch and takes the whole furni load down. Anything unreadable is simply no
     * configuration.
     */
    private static JsonData parsePayload(String wiredData) {
        try {
            return WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (Exception exception) {
            return null;
        }
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
