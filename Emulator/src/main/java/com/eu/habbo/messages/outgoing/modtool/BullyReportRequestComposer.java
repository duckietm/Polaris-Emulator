package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * GuideReportingStatus (3463): the answer to RequestReportUserBullying. The status codes are the
 * official client's (0 ok, 1 pending ticket, 2 blocked, 3 reporting too quickly).
 *
 * <p>Only a pending ticket carries anything after the status. The official reader of the pending
 * ticket takes {@code type, secondsAgo, isGuide} and then a set of strings that depends on the
 * type: 0 and 2 (a tour for a new or a returning user) carry name and figure, 1 (a request for
 * instructions) carries name, figure and description, and 3 (a bully report) carries name, figure
 * and room name unless it is read as a guide.
 */
public class BullyReportRequestComposer extends MessageComposer {
    public static final int START_REPORT = 0;
    public static final int ONGOING_HELPER_CASE = 1;
    public static final int INVALID_REQUESTS = 2;
    public static final int TOO_RECENT = 3;

    public static final int TYPE_TOUR_NEW_USER = 0;
    public static final int TYPE_INSTRUCTIONS = 1;
    public static final int TYPE_TOUR_RETURNING_USER = 2;
    public static final int TYPE_BULLY_REPORT = 3;

    private final int errorCode;
    private final GuardianTicket pendingTicket;

    public BullyReportRequestComposer(int errorCode) {
        this(errorCode, null);
    }

    /**
     * Kept for plugins built against the old signature. The type no longer goes on the wire by
     * itself: a pending case needs its ticket, so without one the answer is "too recent".
     */
    @Deprecated
    public BullyReportRequestComposer(int errorCode, int errorCodeType) {
        this(errorCode, null);
    }

    /** A pending bully report of the reporter: the reported user, how long ago and in which room. */
    public BullyReportRequestComposer(int errorCode, GuardianTicket pendingTicket) {
        this.errorCode = errorCode;
        this.pendingTicket = pendingTicket;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BullyReportRequestComposer);

        if (this.errorCode == ONGOING_HELPER_CASE && this.pendingTicket == null) {
            // Without a ticket to describe there is nothing the client could read after the code.
            this.response.appendInt(TOO_RECENT);
            return this.response;
        }

        this.response.appendInt(this.errorCode);

        if (this.errorCode == ONGOING_HELPER_CASE) {
            long secondsAgo = Math.max(
                    0L,
                    (System.currentTimeMillis() - this.pendingTicket.getDate().getTime()) / 1000L);

            this.response.appendInt(TYPE_BULLY_REPORT);
            this.response.appendInt((int) Math.min(Integer.MAX_VALUE, secondsAgo));
            this.response.appendBoolean(false);
            this.response.appendString(this.pendingTicket.getReportedName());
            this.response.appendString(this.pendingTicket.getReportedFigure());
            this.response.appendString(this.pendingTicket.getRoomName());
        }

        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    /** Kept for plugins built against the old signature: a pending case is always a bully report. */
    @Deprecated
    public int getErrorCodeType() {
        return this.pendingTicket != null ? TYPE_BULLY_REPORT : 0;
    }

    public GuardianTicket getPendingTicket() {
        return pendingTicket;
    }
}
