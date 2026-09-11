package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class BullyReportRequestComposer extends MessageComposer {
    public static final int START_REPORT = 0;
    public static final int ONGOING_HELPER_CASE = 1;
    public static final int INVALID_REQUESTS = 2;
    public static final int TOO_RECENT = 3;

    /** The kinds of pending case, as the client reads them: each carries a different set of fields. */
    public static final int TYPE_GUIDE_SESSION = 0;

    public static final int TYPE_BULLY_REPORT = 1;
    public static final int TYPE_HELPER_SESSION = 2;
    public static final int TYPE_ROOM_REPORT = 3;

    private final int errorCode;
    private final int errorCodeType;

    public BullyReportRequestComposer(int errorCode, int errorCodeType) {
        this.errorCode = errorCode;
        this.errorCodeType = errorCodeType;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BullyReportRequestComposer);
        this.response.appendInt(this.errorCode);

        // Only a pending case says anything more: every other answer is the code alone, and the
        // client stops reading there.
        if (this.errorCode == ONGOING_HELPER_CASE) {
            this.response.appendInt(this.errorCodeType);
            this.response.appendInt(1); // How long ago it was opened.

            // A room report read as a guide carries no other party at all, so the flag has to agree
            // with the strings that follow or the client reads past the end of the packet.
            this.response.appendBoolean(this.errorCodeType != TYPE_ROOM_REPORT);

            if (this.errorCodeType == TYPE_ROOM_REPORT) {
                return this.response;
            }

            this.response.appendString("");
            this.response.appendString("");

            if (this.errorCodeType == TYPE_BULLY_REPORT) {
                this.response.appendString("");
            }
        }

        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getErrorCodeType() {
        return errorCodeType;
    }
}
