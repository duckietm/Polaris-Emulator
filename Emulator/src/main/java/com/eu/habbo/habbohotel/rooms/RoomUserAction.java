package com.eu.habbo.habbohotel.rooms;

public enum RoomUserAction {
    NONE(0),
    WAVE(1),
    BLOW_KISS(2),
    LAUGH(3),
    UNKNOWN(4),
    IDLE(5),
    JUMP(6),
    THUMB_UP(7),
    SIX_SEVEN(67);

    private final int action;

    RoomUserAction(int action) {
        this.action = action;
    }

    public static RoomUserAction fromValue(int value) {
        for (RoomUserAction action : RoomUserAction.values()) {
            if (action.getAction() == value) {
                return action;
            }
        }

        return NONE;
    }

    public int getAction() {
        return this.action;
    }
}
