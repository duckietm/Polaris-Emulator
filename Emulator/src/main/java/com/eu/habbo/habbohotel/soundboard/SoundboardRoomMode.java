package com.eu.habbo.habbohotel.soundboard;

/**
 * Who may play pads in a room. The wire code is also what rooms.soundboard_enabled stores, so
 * the two values a room could always have, 0 and 1, keep meaning off and everybody.
 */
public enum SoundboardRoomMode {
    OFF(0),
    EVERYONE(1),
    RIGHTS(2);

    private final int wireCode;

    SoundboardRoomMode(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return this.wireCode;
    }

    public boolean enabled() {
        return this != OFF;
    }

    public boolean allows(boolean hasRights) {
        return this == EVERYONE || (this == RIGHTS && hasRights);
    }

    /** A code nobody defined is read as off, the safe side. */
    public static SoundboardRoomMode fromWire(int wireCode) {
        for (SoundboardRoomMode mode : values()) {
            if (mode.wireCode == wireCode) {
                return mode;
            }
        }

        return OFF;
    }
}
