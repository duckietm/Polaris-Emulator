package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
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
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectUserToFurni extends WiredEffectUserFurniBase {
    private static final int WALKMODE_IF_CLOSER = 0;
    private static final int WALKMODE_CONTINUE = 1;
    private static final int WALKMODE_STOP = 2;

    public static final WiredEffectType type = WiredEffectType.USER_TO_FURNI;
    private int walkMode = WALKMODE_CONTINUE;

    public WiredEffectUserToFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUserToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        HabboItem item = this.resolveLastItem(ctx);
        WiredMovementPhysics movementPhysics = WiredMoveCarryHelper.getUserMovementPhysics(room, this, ctx);

        if (room == null || item == null) {
            return;
        }

        RoomTile targetTile = room.getLayout().getTile(item.getX(), item.getY());
        if (targetTile == null) {
            return;
        }

        for (Habbo habbo : this.resolveHabbos(room, ctx)) {
            this.moveHabboSmooth(room, habbo, item, targetTile, movementPhysics);
        }
    }

    @Deprecated
    @Override
    public boolean execute(com.eu.habbo.habbohotel.rooms.RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.getDelay(),
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
            this.furniSource,
            this.userSource,
            this.walkMode
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
            this.walkMode = this.normalizeWalkMode((data.walkMode != null) ? data.walkMode : WALKMODE_CONTINUE);

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

            return;
        }

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
        this.walkMode = WALKMODE_CONTINUE;
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.walkMode = WALKMODE_CONTINUE;
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
        message.appendInt(3);
        message.appendInt(this.furniSource);
        message.appendInt(this.userSource);
        message.appendInt(this.walkMode);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
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
        this.walkMode = this.normalizeWalkMode((settings.getIntParams().length > 2) ? settings.getIntParams()[2] : WALKMODE_CONTINUE);

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

    private void moveHabboSmooth(Room room, Habbo habbo, HabboItem item, RoomTile targetTile, WiredMovementPhysics movementPhysics) {
        if (room == null || habbo == null || item == null || targetTile == null || habbo.getRoomUnit() == null) {
            return;
        }

        RoomUnit roomUnit = habbo.getRoomUnit();
        RoomTile oldLocation = roomUnit.getCurrentLocation();
        RoomTile previousGoal = roomUnit.getGoal();
        boolean wasWalking = roomUnit.isWalking();
        boolean noAnimation = WiredMoveCarryHelper.hasNoAnimationExtra(room, this);

        if (oldLocation == null) {
            return;
        }

        double newZ = item.getZ() + Item.getCurrentHeight(item);
        int animationDuration = noAnimation ? 0 : WiredMoveCarryHelper.getAnimationDuration(room, this, WiredUserMovementHelper.DEFAULT_ANIMATION_DURATION);
        if (!WiredUserMovementHelper.moveUser(room, roomUnit, targetTile, newZ,
                roomUnit.getBodyRotation(), roomUnit.getHeadRotation(), animationDuration, noAnimation, movementPhysics)) {
            return;
        }

        this.applyWalkMode(roomUnit, oldLocation, previousGoal, targetTile, wasWalking,
                animationDuration);
        roomUnit.setPreviousLocationZ(roomUnit.getZ());
    }

    private void applyWalkMode(RoomUnit roomUnit, RoomTile oldLocation, RoomTile previousGoal, RoomTile targetTile, boolean wasWalking, int delay) {
        if (roomUnit == null || targetTile == null) {
            return;
        }

        Runnable applyGoal = () -> {
            if (roomUnit.getCurrentLocation() == null
                    || roomUnit.isWalking()
                    || roomUnit.hasStatus(RoomUnitStatus.SIT)
                    || roomUnit.hasStatus(RoomUnitStatus.LAY)
                    || roomUnit.getCurrentLocation().x != targetTile.x
                    || roomUnit.getCurrentLocation().y != targetTile.y) {
                return;
            }

            if (this.walkMode == WALKMODE_STOP || !wasWalking || previousGoal == null) {
                roomUnit.setGoalLocation(targetTile);
                return;
            }

            if (this.walkMode == WALKMODE_IF_CLOSER && !this.isCloserToGoal(oldLocation, targetTile, previousGoal)) {
                roomUnit.setGoalLocation(targetTile);
                return;
            }

            roomUnit.setGoalLocation(previousGoal);
        };

        if (delay > 0) {
            Emulator.getThreading().run(applyGoal, delay);
            return;
        }

        applyGoal.run();
    }

    private boolean isCloserToGoal(RoomTile oldLocation, RoomTile newLocation, RoomTile goalLocation) {
        if (oldLocation == null || newLocation == null || goalLocation == null) {
            return false;
        }

        return this.distanceSquared(newLocation, goalLocation) < this.distanceSquared(oldLocation, goalLocation);
    }

    private int distanceSquared(RoomTile first, RoomTile second) {
        int dx = first.x - second.x;
        int dy = first.y - second.y;
        return (dx * dx) + (dy * dy);
    }

    private int normalizeWalkMode(int walkMode) {
        if (walkMode < WALKMODE_IF_CLOSER || walkMode > WALKMODE_STOP) {
            return WALKMODE_CONTINUE;
        }

        return walkMode;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int furniSource;
        int userSource;
        Integer walkMode;

        public JsonData(int delay, List<Integer> itemIds, int furniSource, int userSource, int walkMode) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.userSource = userSource;
            this.walkMode = walkMode;
        }
    }
}
