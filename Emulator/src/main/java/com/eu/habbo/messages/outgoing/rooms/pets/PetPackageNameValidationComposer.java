package com.eu.habbo.messages.outgoing.rooms.pets;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class PetPackageNameValidationComposer extends MessageComposer {
    public static final int CLOSE_WIDGET = 0;
    public static final int NAME_TOO_SHORT = 1;
    public static final int NAME_TOO_LONG = 2;
    public static final int CONTAINS_INVALID_CHARS = 3;
    public static final int FORBIDDEN_WORDS = 4;

    private final int itemId;
    private final int errorCode;
    private final String errorString;

    public PetPackageNameValidationComposer(int itemId, int errorCode, String errorString) {
        this.itemId = itemId;
        this.errorCode = errorCode;
        this.errorString = errorString;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetPackageNameValidationComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.itemId));
        this.response.appendInt(this.errorCode);
        this.response.appendString(this.errorString);
        return this.response;
    }

    public int getItemId() {
        return itemId;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorString() {
        return errorString;
    }
}
