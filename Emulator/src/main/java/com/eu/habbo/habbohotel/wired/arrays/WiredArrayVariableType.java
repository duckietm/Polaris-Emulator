package com.eu.habbo.habbohotel.wired.arrays;

/** Array protocol type IDs retained from Seth's editor contract. */
public enum WiredArrayVariableType {
    FURNI(0),
    ROOM(1),
    USER(2),
    CONTEXT(3);

    private final int code;

    WiredArrayVariableType(int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }

    public static WiredArrayVariableType fromCode(int code) {
        for (WiredArrayVariableType type : values()) {
            if (type.code == code) return type;
        }
        return ROOM;
    }
}
