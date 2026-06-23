package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Passes when a furni ADJACENT to the wired trigger furni currently has its {@code extradata} state
 * equal to a required value. "Adjacent" means any of the 8 tiles surrounding the trigger furni's
 * tile (computed via {@link com.eu.habbo.habbohotel.rooms.RoomLayout#getTilesAround(RoomTile)}); items
 * on each neighbour tile are read with {@link Room#getItemsAt(RoomTile)} and their state via
 * {@link HabboItem#getExtradata()}.
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

    private String requiredState = "";
    private final THashSet<Integer> selectedFurni = new THashSet<>();

    public WiredConditionTriggerFurniAdjacentState(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTriggerFurniAdjacentState(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

        HabboItem trigger = ctx.triggerItem();
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
        THashSet<Integer> allowedBaseItems = new THashSet<>();
        for (int itemId : this.selectedFurni) {
            HabboItem selected = room.getHabboItem(itemId);
            if (selected != null) {
                allowedBaseItems.add(selected.getBaseItem().getId());
            }
        }

        List<RoomTile> neighbours = room.getLayout().getTilesAround(triggerTile);

        for (RoomTile tile : neighbours) {
            if (tile == null) {
                continue;
            }

            for (HabboItem item : room.getItemsAt(tile)) {
                if (item == null || item.getId() == trigger.getId()) {
                    continue;
                }

                if (!allowedBaseItems.isEmpty() && !allowedBaseItems.contains(item.getBaseItem().getId())) {
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
        String state = settings.getStringParam();
        this.requiredState = (state == null) ? "" : state;

        this.selectedFurni.clear();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        int[] furniIds = settings.getFurniIds();
        if (furniIds != null) {
            int maxFurni = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", WiredManager.MAXIMUM_FURNI_SELECTION);
            if (furniIds.length > maxFurni) {
                return false;
            }

            for (int itemId : furniIds) {
                if (room == null) {
                    this.selectedFurni.add(itemId);
                    continue;
                }

                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    this.selectedFurni.add(item.getId());
                }
            }
        }

        return true;
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

            this.requiredState = (data.state == null) ? "" : data.state;

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

        THashSet<Integer> remove = new THashSet<>();
        for (int itemId : this.selectedFurni) {
            if (room.getHabboItem(itemId) == null) {
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
