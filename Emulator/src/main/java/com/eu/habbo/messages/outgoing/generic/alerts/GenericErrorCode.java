package com.eu.habbo.messages.outgoing.generic.alerts;

public enum GenericErrorCode {
    AUTHENTICATION_FAILED(-3),
    CONNECTING_TO_SERVER_FAILED(-400),
    KICKED_OUT_OF_ROOM(4008),
    VIP_REQUIRED(4009),
    ROOM_NAME_UNACCEPTABLE(4010),
    CANNOT_BAN_GROUP_MEMBER(4011),
    WRONG_ROOM_PASSWORD(-100002),
    TRADE_STRIP_LOCKED(-13001);

    private final int code;

    GenericErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }
}
