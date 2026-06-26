package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class BullyReportRequestComposer extends MessageComposer {
    public static final int START_REPORT = 0;
    public static final int ONGOING_HELPER_CASE = 1;
    public static final int INVALID_REQUESTS = 2;
    public static final int TOO_RECENT = 3;

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

        if (this.errorCode == ONGOING_HELPER_CASE) {
            this.response.appendInt(this.errorCodeType);
            this.response.appendInt(1); //Timestamp
            this.response.appendBoolean(true); //Pending guide session.

            this.response.appendString("admin");
            this.response.appendString("ca-1807-64.lg-3365-78.hr-3370-42-31.hd-3093-1359.ch-3372-65");
            switch (this.errorCodeType) {
                case 3 -> this.response.appendString("room Name");
                case 1 -> this.response.appendString("description");
            }
        }
        //:test 1917 i:1 i:3 i:1 b:0 s:1 s:1 s:1
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getErrorCodeType() {
        return errorCodeType;
    }
}
