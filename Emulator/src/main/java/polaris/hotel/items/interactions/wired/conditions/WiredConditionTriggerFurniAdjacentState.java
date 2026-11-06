package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;

/**
 * Passes when a furni ADJACENT to the wired trigger furni currently has its {@code extradata} state
 * equal to a required value. "Adjacent" means any of the 8 tiles surrounding the trigger furni's
 * tile (computed via {@link polaris.hotel.rooms.RoomLayout#getTilesAround(RoomTile)}); items
 * on each neighbour tile are read with {@link Room#getItemsAt(RoomTile)} and their state via
 * {@link UserItem#getExtradata()}.
 *
 * <p>Inputs: one {@code stringParam} carrying the required state, plus an optional furni picker. When
 * the picker selects one or more furni, only adjacent items whose base-item type matches one of the
 * selected furni count (a way to scope the check to a particular furni kind); when nothing is selected
 * the condition matches any adjacent furni whose state equals the required value.</p>
 *
 * <p>Carries no int params: it serializes {@code intParamCount=0} and the required state in the string
 * slot, mirroring {@link WiredConditionMatchStatePosition}'s furni-selecting serialize shape (furni
 * flag true + {@code MAXIMUM_FURNI_SELECTION} + selected ids). Uses the dedicated new client dialog
 * {@code WiredConditionlayout.TRG_FURNI_ADJACENT_STATE} (46).</p>
 */
public class WiredConditionTriggerFurniAdjacentState extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.TRG_FURNI_ADJACENT_STATE;

    /** The cap {@link WiredConditionUserOnFurniWithState} puts on its state. */
    private static final int MAX_STATE_LENGTH = 256;

    private String requiredState = "";
    private final HashSet<Integer> selectedFurni = new HashSet<>();

    public WiredConditionTriggerFurniAdjacentState(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTriggerFurniAdjacentState(
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

        Room room = ctx.room();

        UserItem trigger = ctx.triggerItem();
        if (trigger == null) {
            return false;
        }

        RoomTile triggerTile = room.getLayout() == null
                ? null
                : room.getLayout().getTile((short) trigger.getX(), (short) trigger.getY());

        if (triggerTile == null) {
            return false;
        }

        // Resolve the set of base-item ids the picker scopes the check to (empty == any furni).
        var allowedBaseItems = new HashSet<Integer>();
        for (int itemId : this.selectedFurni) {
            UserItem selected = room.getUserItem(itemId);
            if (selected != null && selected.getBaseItem() != null) {
                allowedBaseItems.add(selected.getBaseItem().getId());
            }
        }

        List<RoomTile> neighbours = room.getLayout().getTilesAround(triggerTile);

        for (RoomTile tile : neighbours) {
            if (tile == null) {
                continue;
            }

            for (UserItem item : room.getItemsAt(tile)) {
                if (item == null || item.getId() == trigger.getId()) {
                    continue;
                }

                // A furni without a base item has no type to scope by; it only counts when nothing is.
                if (!allowedBaseItems.isEmpty()
                        && (item.getBaseItem() == null
                                || !allowedBaseItems.contains(item.getBaseItem().getId()))) {
                    continue;
                }

                String extradata = item.getExtradata();
                if (extradata != null && extradata.equals(this.requiredState)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(true);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.selectedFurni.size());

        for (int itemId : this.selectedFurni) {
            message.appendInt(itemId);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.requiredState);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.requiredState = normalizeRequiredState(settings.getStringParam());
        this.selectedFurni.clear();

        int[] furniIds = settings.getFurniIds();
        if (furniIds == null || furniIds.length == 0) {
            return true;
        }

        int maxFurni =
                Emulator.getConfig().getInt("hotel.wired.furni.selection.count", WiredManager.MAXIMUM_FURNI_SELECTION);
        if (furniIds.length > maxFurni) {
            return false;
        }

        // Only furni the room can resolve are kept: an id nobody can look up is not a selection.
        Room room = this.resolveRoom(settings);
        if (room == null) {
            return true;
        }

        for (int itemId : furniIds) {
            UserItem item = room.getUserItem(itemId);
            if (item != null) {
                this.selectedFurni.add(item.getId());
            }
        }

        return true;
    }

    /** The room this box stands in, looked up through the room manager. */
    private Room resolveRoom(WiredSettings settings) {
        GameEnvironment environment = Emulator.getGameEnvironment();
        if (environment == null || environment.getRoomManager() == null) {
            return null;
        }

        return environment.getRoomManager().getRoom(this.getRoomId());
    }

    /** Trimmed and capped, as {@link WiredConditionUserOnFurniWithState#normalizeRequiredState} does it. */
    static String normalizeRequiredState(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.length() > MAX_STATE_LENGTH) {
            return trimmed.substring(0, MAX_STATE_LENGTH);
        }

        return trimmed;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.requiredState, new ArrayList<>(this.selectedFurni)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data;
            try {
                data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            } catch (RuntimeException exception) {
                this.onPickUp();
                return;
            }

            if (data == null) {
                return;
            }

            this.requiredState = normalizeRequiredState(data.state);

            if (data.furni != null) {
                for (Integer itemId : data.furni) {
                    if (itemId != null) {
                        this.selectedFurni.add(itemId);
                    }
                }
            }
        }

        this.refresh(room);
    }

    @Override
    public void onPickUp() {
        this.requiredState = "";
        this.selectedFurni.clear();
    }

    private void refresh(Room room) {
        if (room == null) {
            return;
        }

        var remove = new HashSet<Integer>();
        for (int itemId : this.selectedFurni) {
            if (room.getUserItem(itemId) == null) {
                remove.add(itemId);
            }
        }

        for (int itemId : remove) {
            this.selectedFurni.remove(itemId);
        }
    }

    static class JsonData {
        String state;
        List<Integer> furni;

        public JsonData(String state, List<Integer> furni) {
            this.state = state;
            this.furni = furni;
        }
    }
}
