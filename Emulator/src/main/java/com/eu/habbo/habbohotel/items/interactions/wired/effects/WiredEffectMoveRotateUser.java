package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
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

public class WiredEffectMoveRotateUser extends InteractionWiredEffect {
    private static final int ROTATION_CLOCKWISE = 8;
    private static final int ROTATION_COUNTER_CLOCKWISE = 9;

    public static final WiredEffectType type = WiredEffectType.MOVE_ROTATE_USER;

    private int movementDirection = -1;
    private int rotationDirection = -1;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMoveRotateUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveRotateUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        WiredMovementPhysics movementPhysics = WiredMoveCarryHelper.getUserMovementPhysics(room, this, ctx);

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            if (roomUnit == null || roomUnit.getRoom() != room) {
                continue;
            }

            boolean hasRotation = this.rotationDirection >= 0;
            RoomUserRotation targetBodyRotation = hasRotation ? this.getTargetRotation(roomUnit) : roomUnit.getBodyRotation();
            RoomUserRotation targetHeadRotation = hasRotation ? targetBodyRotation : roomUnit.getHeadRotation();

            if (roomUnit.isWalking()) {
                if (hasRotation) {
                    WiredUserMovementHelper.updateUserDirection(room, roomUnit, targetBodyRotation, targetHeadRotation);
                }
                continue;
            }

            RoomTile targetTile = (this.movementDirection >= 0) ? this.getTargetTile(room, roomUnit, this.movementDirection) : null;
            boolean canMove = this.canMoveTo(room, roomUnit, targetTile, movementPhysics);
            boolean noAnimation = WiredMoveCarryHelper.hasNoAnimationExtra(room, this);
            int animationDuration = noAnimation ? 0 : WiredMoveCarryHelper.getAnimationDuration(room, this, WiredUserMovementHelper.DEFAULT_ANIMATION_DURATION);

            if (canMove) {
                double targetZ = targetTile.getStackHeight() + ((targetTile.state == RoomTileState.SIT) ? -0.5 : 0);
                if (!WiredUserMovementHelper.moveUser(room, roomUnit, targetTile, targetZ, targetBodyRotation, targetHeadRotation,
                        animationDuration, noAnimation, movementPhysics)) {
                    if (hasRotation) {
                        WiredUserMovementHelper.updateUserDirection(room, roomUnit, targetBodyRotation, targetHeadRotation);
                    }
                }
                continue;
            }

            if (hasRotation) {
                WiredUserMovementHelper.updateUserDirection(room, roomUnit, targetBodyRotation, targetHeadRotation);
            }
        }
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.getDelay(),
            this.movementDirection,
            this.rotationDirection,
            this.userSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.movementDirection = this.normalizeDirection(data.movementDirection);
            this.rotationDirection = this.normalizeRotation(data.rotationDirection);
            this.userSource = data.userSource;
            return;
        }

        this.setDelay(0);
        this.movementDirection = -1;
        this.rotationDirection = -1;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.movementDirection = -1;
        this.rotationDirection = -1;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.movementDirection);
        message.appendInt(this.rotationDirection);
        message.appendInt(this.userSource);
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
        if (settings.getIntParams().length < 3) {
            throw new WiredSaveException("Invalid data");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.movementDirection = this.normalizeDirection(settings.getIntParams()[0]);
        this.rotationDirection = this.normalizeRotation(settings.getIntParams()[1]);
        this.userSource = settings.getIntParams()[2];
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_MOVEMENT;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private int normalizeDirection(int direction) {
        return (direction >= 0 && direction <= 7) ? direction : -1;
    }

    private int normalizeRotation(int rotation) {
        return ((rotation >= 0 && rotation <= 7) || rotation == ROTATION_CLOCKWISE || rotation == ROTATION_COUNTER_CLOCKWISE) ? rotation : -1;
    }

    private RoomUserRotation getTargetRotation(RoomUnit roomUnit) {
        RoomUserRotation currentRotation = (roomUnit != null && roomUnit.getBodyRotation() != null) ? roomUnit.getBodyRotation() : RoomUserRotation.NORTH;

        if (this.rotationDirection == ROTATION_CLOCKWISE) {
            return RoomUserRotation.clockwise(currentRotation);
        }

        if (this.rotationDirection == ROTATION_COUNTER_CLOCKWISE) {
            return RoomUserRotation.counterClockwise(currentRotation);
        }

        return RoomUserRotation.fromValue(this.rotationDirection);
    }

    private RoomTile getTargetTile(Room room, RoomUnit roomUnit, int direction) {
        RoomTile currentTile = roomUnit.getCurrentLocation();

        if (currentTile == null) {
            return null;
        }

        int deltaX = 0;
        int deltaY = 0;

        switch (RoomUserRotation.fromValue(direction)) {
            case NORTH:
                deltaY = 1;
                break;
            case NORTH_EAST:
                deltaX = 1;
                deltaY = 1;
                break;
            case EAST:
                deltaX = 1;
                break;
            case SOUTH_EAST:
                deltaX = 1;
                deltaY = -1;
                break;
            case SOUTH:
                deltaY = -1;
                break;
            case SOUTH_WEST:
                deltaX = -1;
                deltaY = -1;
                break;
            case WEST:
                deltaX = -1;
                break;
            case NORTH_WEST:
                deltaX = -1;
                deltaY = 1;
                break;
        }

        return room.getLayout().getTile((short) (currentTile.x + deltaX), (short) (currentTile.y + deltaY));
    }

    private boolean canMoveTo(Room room, RoomUnit roomUnit, RoomTile targetTile, WiredMovementPhysics movementPhysics) {
        return WiredUserMovementHelper.canMoveTo(room, roomUnit, targetTile, movementPhysics);
    }

    public static boolean handleWalkWhileActive(Room room, RoomUnit roomUnit, RoomTile targetTile) {
        return false;
    }

    static class JsonData {
        int delay;
        int movementDirection;
        int rotationDirection;
        int userSource;

        public JsonData(int delay, int movementDirection, int rotationDirection, int userSource) {
            this.delay = delay;
            this.movementDirection = movementDirection;
            this.rotationDirection = rotationDirection;
            this.userSource = userSource;
        }
    }
}
