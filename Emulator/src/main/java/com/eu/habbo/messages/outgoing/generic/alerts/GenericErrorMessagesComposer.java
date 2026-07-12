package com.eu.habbo.messages.outgoing.generic.alerts;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class GenericErrorMessagesComposer extends MessageComposer {
    /** @deprecated Use {@link GenericErrorCode#AUTHENTICATION_FAILED}. */
    @Deprecated public static final int AUTHENTICATION_FAILED = GenericErrorCode.AUTHENTICATION_FAILED.code();
    /** @deprecated Use {@link GenericErrorCode#CONNECTING_TO_SERVER_FAILED}. */
    @Deprecated public static final int CONNECTING_TO_THE_SERVER_FAILED = GenericErrorCode.CONNECTING_TO_SERVER_FAILED.code();
    /** @deprecated Use {@link GenericErrorCode#KICKED_OUT_OF_ROOM}. */
    @Deprecated public static final int KICKED_OUT_OF_THE_ROOM = GenericErrorCode.KICKED_OUT_OF_ROOM.code();
    /** @deprecated Use {@link GenericErrorCode#VIP_REQUIRED}. */
    @Deprecated public static final int NEED_TO_BE_VIP = GenericErrorCode.VIP_REQUIRED.code();
    /** @deprecated Use {@link GenericErrorCode#ROOM_NAME_UNACCEPTABLE}. */
    @Deprecated public static final int ROOM_NAME_UNACCEPTABLE = GenericErrorCode.ROOM_NAME_UNACCEPTABLE.code();
    /** @deprecated Use {@link GenericErrorCode#CANNOT_BAN_GROUP_MEMBER}. */
    @Deprecated public static final int CANNOT_BAN_GROUP_MEMBER = GenericErrorCode.CANNOT_BAN_GROUP_MEMBER.code();
    /** @deprecated Use {@link GenericErrorCode#WRONG_ROOM_PASSWORD}. */
    @Deprecated public static final int WRONG_PASSWORD_USED = GenericErrorCode.WRONG_ROOM_PASSWORD.code();

    private final int errorCode;

    public GenericErrorMessagesComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    public GenericErrorMessagesComposer(GenericErrorCode errorCode) {
        this(errorCode.code());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GenericErrorMessages);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
