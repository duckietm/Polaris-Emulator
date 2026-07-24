package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class WiredMovementsComposer extends MessageComposer {
    public static final int TYPE_USER_MOVE = 0;
    public static final int TYPE_FURNI_MOVE = 1;
    public static final int TYPE_WALL_ITEM_MOVE = 2;
    public static final int TYPE_USER_DIRECTION = 3;

    public static final int FURNI_ANCHOR_NONE = 0;
    public static final int FURNI_ANCHOR_USER = 1;
    public static final int FURNI_ANCHOR_FURNI = 2;

    public static final int USER_MOVEMENT_WALK = 0;
    public static final int USER_MOVEMENT_SLIDE = 1;
    public static final int DEFAULT_DURATION = 500;

    private final List<MovementData> movements;

    public WiredMovementsComposer(List<MovementData> movements) {
        this.movements = normalizeMovements(movements == null ? new ArrayList<>() : movements);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredMovementsComposer);
        this.response.appendInt(this.movements.size());

        for (MovementData movement : this.movements) {
            this.response.appendInt(movement.getType());
            movement.append(this.response);
        }

        return this.response;
    }

    private static List<MovementData> normalizeMovements(List<MovementData> source)
    {
        if((source == null) || source.isEmpty()) return new ArrayList<>();

        final LinkedHashMap<String, MovementData> normalized = new LinkedHashMap<>();

        for(final MovementData movement : source)
        {
            if(movement == null) continue;

            final String key = movementKey(movement);

            if(key == null)
            {
                normalized.put(UUID.randomUUID().toString(), movement);
                continue;
            }

            final MovementData existing = normalized.get(key);

            if(existing == null)
            {
                normalized.put(key, movement);
                continue;
            }

            normalized.put(key, mergeMovement(existing, movement));
        }

        return new ArrayList<>(normalized.values());
    }

    private static String movementKey(MovementData movement)
    {
        if(movement instanceof FurniMovementData)
        {
            return "furni:" + ((FurniMovementData) movement).id;
        }

        if(movement instanceof UserMovementData)
        {
            return "user:" + ((UserMovementData) movement).id;
        }

        if(movement instanceof UserDirectionData)
        {
            return "userdir:" + ((UserDirectionData) movement).id;
        }

        if(movement instanceof WallItemMovementData)
        {
            return "wall:" + ((WallItemMovementData) movement).id;
        }

        return null;
    }

    private static MovementData mergeMovement(MovementData previous, MovementData current)
    {
        if((previous instanceof FurniMovementData) && (current instanceof FurniMovementData))
        {
            final FurniMovementData oldMovement = (FurniMovementData) previous;
            final FurniMovementData newMovement = (FurniMovementData) current;

            return furniMovement(
                oldMovement.id,
                oldMovement.fromX,
                oldMovement.fromY,
                newMovement.toX,
                newMovement.toY,
                oldMovement.fromZ,
                newMovement.toZ,
                newMovement.rotation,
                newMovement.duration,
                newMovement.elapsed,
                newMovement.anchorType,
                newMovement.anchorId);
        }

        if((previous instanceof UserMovementData) && (current instanceof UserMovementData))
        {
            final UserMovementData oldMovement = (UserMovementData) previous;
            final UserMovementData newMovement = (UserMovementData) current;

            return new UserMovementData(
                oldMovement.id,
                oldMovement.fromX,
                oldMovement.fromY,
                newMovement.toX,
                newMovement.toY,
                oldMovement.fromZ,
                newMovement.toZ,
                newMovement.movementType,
                newMovement.bodyDirection,
                newMovement.headDirection,
                newMovement.duration);
        }

        return current;
    }

    public static MovementData furniMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ) {
        return furniMovement(id, fromX, fromY, toX, toY, fromZ, toZ, 0, DEFAULT_DURATION, 0, FURNI_ANCHOR_NONE, 0);
    }

    public static MovementData furniMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int duration) {
        return furniMovement(id, fromX, fromY, toX, toY, fromZ, toZ, rotation, duration, 0, FURNI_ANCHOR_NONE, 0);
    }

    public static MovementData furniMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int duration, int elapsed) {
        return furniMovement(id, fromX, fromY, toX, toY, fromZ, toZ, rotation, duration, elapsed, FURNI_ANCHOR_NONE, 0);
    }

    public static MovementData furniMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int duration, int elapsed, int anchorType, int anchorId) {
        return new FurniMovementData(id, fromX, fromY, toX, toY, fromZ, toZ, rotation, duration, elapsed, anchorType, anchorId);
    }

    public static MovementData userWalkMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int bodyDirection, int headDirection, int duration) {
        return new UserMovementData(id, fromX, fromY, toX, toY, fromZ, toZ, USER_MOVEMENT_WALK, bodyDirection, headDirection, duration);
    }

    public static MovementData userSlideMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int bodyDirection, int headDirection, int duration) {
        return new UserMovementData(id, fromX, fromY, toX, toY, fromZ, toZ, USER_MOVEMENT_SLIDE, bodyDirection, headDirection, duration);
    }

    public static MovementData userDirectionUpdate(int id, int headDirection, int bodyDirection) {
        return new UserDirectionData(id, headDirection, bodyDirection);
    }

    public static MovementData wallItemMovement(int id, boolean enabled, int[] values) {
        return new WallItemMovementData(id, enabled, values);
    }

    public interface MovementData {
        int getType();

        void append(ServerMessage response);
    }

    private abstract static class BaseMovementData implements MovementData {
        private final int type;

        private BaseMovementData(int type) {
            this.type = type;
        }

        @Override
        public int getType() {
            return this.type;
        }
    }

    private static final class UserMovementData extends BaseMovementData {
        private final int id;
        private final int fromX;
        private final int fromY;
        private final int toX;
        private final int toY;
        private final double fromZ;
        private final double toZ;
        private final int movementType;
        private final int bodyDirection;
        private final int headDirection;
        private final int duration;

        private UserMovementData(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int movementType, int bodyDirection, int headDirection, int duration) {
            super(TYPE_USER_MOVE);
            this.id = id;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.fromZ = fromZ;
            this.toZ = toZ;
            this.movementType = movementType;
            this.bodyDirection = bodyDirection;
            this.headDirection = headDirection;
            this.duration = duration;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.fromX);
            response.appendInt(this.fromY);
            response.appendInt(this.toX);
            response.appendInt(this.toY);
            response.appendString(Double.toString(this.fromZ));
            response.appendString(Double.toString(this.toZ));
            response.appendInt(this.id);
            response.appendInt(this.movementType);
            response.appendInt(this.bodyDirection);
            response.appendInt(this.headDirection);
            response.appendInt(this.duration);
        }
    }

    private static final class FurniMovementData extends BaseMovementData {
        private final int id;
        private final int fromX;
        private final int fromY;
        private final int toX;
        private final int toY;
        private final double fromZ;
        private final double toZ;
        private final int rotation;
        private final int duration;
        private final int elapsed;
        private final int anchorType;
        private final int anchorId;

        private FurniMovementData(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int duration, int elapsed, int anchorType, int anchorId) {
            super(TYPE_FURNI_MOVE);
            this.id = id;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.fromZ = fromZ;
            this.toZ = toZ;
            this.rotation = rotation;
            this.duration = duration;
            this.elapsed = elapsed;
            this.anchorType = anchorType;
            this.anchorId = anchorId;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.fromX);
            response.appendInt(this.fromY);
            response.appendInt(this.toX);
            response.appendInt(this.toY);
            response.appendString(Double.toString(this.fromZ));
            response.appendString(Double.toString(this.toZ));
            response.appendInt(this.id);
            response.appendInt(this.rotation);
            response.appendInt(this.duration);
            response.appendInt(this.elapsed);
            response.appendInt(this.anchorType);
            response.appendInt(this.anchorId);
        }
    }

    private static final class UserDirectionData extends BaseMovementData {
        private final int id;
        private final int headDirection;
        private final int bodyDirection;

        private UserDirectionData(int id, int headDirection, int bodyDirection) {
            super(TYPE_USER_DIRECTION);
            this.id = id;
            this.headDirection = headDirection;
            this.bodyDirection = bodyDirection;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.id);
            response.appendInt(this.headDirection);
            response.appendInt(this.bodyDirection);
        }
    }

    private static final class WallItemMovementData extends BaseMovementData {
        private final int id;
        private final boolean enabled;
        private final int[] values;

        private WallItemMovementData(int id, boolean enabled, int[] values) {
            super(TYPE_WALL_ITEM_MOVE);
            this.id = id;
            this.enabled = enabled;
            this.values = values == null ? new int[9] : values;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.id);
            response.appendBoolean(this.enabled);

            for (int index = 0; index < 9; index++) {
                response.appendInt(index < this.values.length ? this.values[index] : 0);
            }
        }
    }
}
