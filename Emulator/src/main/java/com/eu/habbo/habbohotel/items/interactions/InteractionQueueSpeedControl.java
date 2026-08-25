package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionQueueSpeedControl extends InteractionRemoteSwitchControl {
    private static final int[] MODE_STATES = new int[]{0, 3, 6, 9};
    private static final int MODE_FRAME_COUNT = 3;
    private static final int BASE_FRAME_DURATION_MS = 500;

    private transient volatile int animationRevision = 0;
    private transient volatile int animationRoomId = 0;
    private transient volatile int animationModeState = Integer.MIN_VALUE;

    public InteractionQueueSpeedControl(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionQueueSpeedControl(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static int toModeState(String extradata) {
        int state = 0;

        try {
            state = Integer.parseInt(extradata);
        } catch (NumberFormatException ignored) {
        }

        if (state >= 9) {
            return 9;
        }

        if (state >= 6) {
            return 6;
        }

        if (state >= 3) {
            return 3;
        }

        return 0;
    }

    public static int toRollerSpeed(String extradata) {
        int modeState = toModeState(extradata);

        if (modeState >= 9) {
            return 3;
        }

        if (modeState >= 6) {
            return 2;
        }

        if (modeState >= 3) {
            return 1;
        }

        return 0;
    }

    public static int toRollerIntervalMs(String extradata) {
        return BASE_FRAME_DURATION_MS * (toRollerSpeed(extradata) + 1);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room == null) {
            return;
        }

        boolean wiredToggle = objects != null
                && objects.length >= 2
                && objects[1] instanceof com.eu.habbo.habbohotel.wired.WiredEffectType;

        if (!wiredToggle) {
            if (client == null) {
                return;
            }

            if (!this.canToggle(client.getHabbo(), room)) {
                super.onClick(client, room, new Object[]{"QUEUE_SPEED_USE"});
                return;
            }
        }

        int nextModeState = getNextModeState(this.getExtradata());
        applyModeState(room, nextModeState, true);

        if (client != null) {
            super.onClick(client, room, new Object[]{"TOGGLE_OVERRIDE"});
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.ensureAnimationLoop(room);
    }

    @Override
    public void onPickUp(Room room) {
        this.animationRevision++;
        this.animationRoomId = 0;
        this.animationModeState = Integer.MIN_VALUE;
        super.onPickUp(room);
    }

    public void ensureAnimationLoop(Room room) {
        if (room == null || !room.isLoaded() || this.getRoomId() != room.getId()) {
            return;
        }

        int modeState = toModeState(this.getExtradata());

        if (this.animationRoomId == room.getId() && this.animationModeState == modeState) {
            return;
        }

        applyModeState(room, modeState, false);
    }

    private void applyModeState(Room room, int modeState, boolean persistSelection) {
        if (room == null) {
            return;
        }

        this.animationRevision++;
        this.animationRoomId = room.getId();
        this.animationModeState = modeState;

        this.setExtradata(Integer.toString(modeState));
        if (persistSelection) {
            this.needsUpdate(true);
        }
        room.updateItemState(this);

        int revision = this.animationRevision;
        int nextFrame = modeState + 1;
        long delay = toRollerIntervalMs(Integer.toString(modeState));

        Emulator.getThreading().run(() -> this.animateNextFrame(room, modeState, nextFrame, revision), delay);
    }

    private void animateNextFrame(Room room, int modeState, int nextFrame, int revision) {
        if (room == null || !room.isLoaded() || this.getRoomId() != room.getId()) {
            return;
        }

        if (revision != this.animationRevision || modeState != this.animationModeState) {
            return;
        }

        int maxFrame = modeState + (MODE_FRAME_COUNT - 1);
        int frame = (nextFrame > maxFrame) ? modeState : nextFrame;

        this.setExtradata(Integer.toString(frame));
        room.updateItemState(this);

        int followingFrame = (frame >= maxFrame) ? modeState : (frame + 1);
        long delay = toRollerIntervalMs(Integer.toString(modeState));

        Emulator.getThreading().run(() -> this.animateNextFrame(room, modeState, followingFrame, revision), delay);
    }

    private static int getNextModeState(String extradata) {
        int currentModeState = toModeState(extradata);

        for (int index = 0; index < MODE_STATES.length; index++) {
            if (MODE_STATES[index] != currentModeState) {
                continue;
            }

            return MODE_STATES[(index + 1) % MODE_STATES.length];
        }

        return MODE_STATES[0];
    }
}
