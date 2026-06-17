package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class WiredEffectUserFurniBase extends InteractionWiredEffect {
    protected final List<HabboItem> items = new ArrayList<>();
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectUserFurniBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUserFurniBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    protected List<HabboItem> resolveItems(WiredContext ctx) {
        Room room = ctx.room();
        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED && room != null) {
            this.items.removeIf(item -> item == null
                    || item.getRoomId() != this.getRoomId()
                    || room.getHabboItem(item.getId()) == null);
        }

        return effectiveItems;
    }

    protected HabboItem resolveLastItem(WiredContext ctx) {
        List<HabboItem> effectiveItems = this.resolveItems(ctx);

        if (effectiveItems.isEmpty()) {
            return null;
        }

        for (int index = effectiveItems.size() - 1; index >= 0; index--) {
            HabboItem item = effectiveItems.get(index);

            if (item != null) {
                return item;
            }
        }

        return null;
    }

    protected Habbo resolveLastHabbo(Room room, WiredContext ctx) {
        Habbo targetHabbo = null;

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);

            if (habbo != null) {
                targetHabbo = habbo;
            }
        }

        return targetHabbo;
    }

    protected List<Habbo> resolveHabbos(Room room, WiredContext ctx) {
        List<Habbo> habbos = new ArrayList<>();

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);

            if (habbo != null) {
                habbos.add(habbo);
            }
        }

        return habbos;
    }

    protected RoomTile resolveTargetTile(Habbo habbo) {
        if (habbo == null || habbo.getRoomUnit() == null) {
            return null;
        }

        RoomUnit roomUnit = habbo.getRoomUnit();
        RoomTile movingTile = this.resolveActiveMoveTile(roomUnit);

        if (movingTile != null) {
            return movingTile;
        }

        return roomUnit.getCurrentLocation();
    }

    private RoomTile resolveActiveMoveTile(RoomUnit roomUnit) {
        if (roomUnit == null || roomUnit.getRoom() == null || roomUnit.getRoom().getLayout() == null) {
            return null;
        }

        String moveStatus = roomUnit.getStatus(RoomUnitStatus.MOVE);
        if (moveStatus != null && !moveStatus.isEmpty()) {
            String[] parts = moveStatus.split(",");
            if (parts.length >= 2) {
                try {
                    return roomUnit.getRoom().getLayout().getTile(
                            Short.parseShort(parts[0]),
                            Short.parseShort(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return null;
    }

    protected Integer resolveFollowAnimationDuration(Room room, Habbo habbo, HabboItem stackItem) {
        if (room == null || habbo == null || habbo.getRoomUnit() == null || stackItem == null) {
            return null;
        }

        RoomUnit roomUnit = habbo.getRoomUnit();
        if (this.resolveActiveMoveTile(roomUnit) == null) {
            return null;
        }

        long moveStatusTimestamp = roomUnit.getMoveStatusTimestamp();
        if (moveStatusTimestamp <= 0L) {
            return null;
        }

        int configuredDuration = WiredMoveCarryHelper.getAnimationDuration(room, stackItem, WiredMovementsComposer.DEFAULT_DURATION);
        int remainingStepDuration = (int) Math.max(50L, WiredMovementsComposer.DEFAULT_DURATION - Math.max(0L, System.currentTimeMillis() - moveStatusTimestamp));
        return Math.min(configuredDuration, remainingStepDuration);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource,
                this.userSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.furniSource = data.furniSource;
            this.userSource = data.userSource;

            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }

            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] wiredDataOld = wiredData.split("\t");

            if (wiredDataOld.length >= 1) {
                this.setDelay(Integer.parseInt(wiredDataOld[0]));
            }

            if (wiredDataOld.length == 2 && wiredDataOld[1].contains(";")) {
                for (String s : wiredDataOld[1].split(";")) {
                    HabboItem item = room.getHabboItem(Integer.parseInt(s));

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }

            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        itemsSnapshot.removeIf(item -> item == null
                || item.getRoomId() != this.getRoomId()
                || room.getHabboItem(item.getId()) == null);
        this.items.clear();
        this.items.addAll(itemsSnapshot);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.furniSource);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        this.furniSource = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = (settings.getIntParams().length > 1) ? settings.getIntParams()[1] : WiredSourceUtil.SOURCE_TRIGGER;

        if (settings.getFurniIds().length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (settings.getFurniIds().length > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        List<HabboItem> newItems = new ArrayList<>();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int itemId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(itemId);

                if (item == null) {
                    throw new WiredSaveException(String.format("Item %s not found", itemId));
                }

                newItems.add(item);
            }
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.items.clear();
        this.items.addAll(newItems);
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int furniSource;
        int userSource;

        public JsonData(int delay, List<Integer> itemIds, int furniSource, int userSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.userSource = userSource;
        }
    }
}
