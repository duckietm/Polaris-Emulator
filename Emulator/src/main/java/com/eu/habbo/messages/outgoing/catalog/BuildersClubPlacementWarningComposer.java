package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Asks the client to confirm a Builders Club placement that would hide the room (AIR
 * BuildersClubPlacementWarning). It echoes the placement so the client can resend it with confirmed set:
 * type, page, offer and extra data, then x, y and direction for floor furni or the wall location.
 */
public class BuildersClubPlacementWarningComposer extends MessageComposer {
    public static final int TYPE_FLOOR = 0;
    public static final int TYPE_WALL = 1;

    private final int type;
    private final int pageId;
    private final int offerId;
    private final String extraData;
    private final int x;
    private final int y;
    private final int direction;
    private final String wallLocation;

    private BuildersClubPlacementWarningComposer(
            int type, int pageId, int offerId, String extraData, int x, int y, int direction, String wallLocation) {
        this.type = type;
        this.pageId = pageId;
        this.offerId = offerId;
        this.extraData = extraData == null ? "" : extraData;
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.wallLocation = wallLocation == null ? "" : wallLocation;
    }

    public static BuildersClubPlacementWarningComposer floor(
            int pageId, int offerId, String extraData, int x, int y, int direction) {
        return new BuildersClubPlacementWarningComposer(TYPE_FLOOR, pageId, offerId, extraData, x, y, direction, null);
    }

    public static BuildersClubPlacementWarningComposer wall(
            int pageId, int offerId, String extraData, String wallLocation) {
        return new BuildersClubPlacementWarningComposer(TYPE_WALL, pageId, offerId, extraData, 0, 0, 0, wallLocation);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BuildersClubPlacementWarningComposer);
        this.response.appendInt(this.type);
        this.response.appendInt(this.pageId);
        this.response.appendInt(this.offerId);
        this.response.appendString(this.extraData);

        if (this.type == TYPE_FLOOR) {
            this.response.appendInt(this.x);
            this.response.appendInt(this.y);
            this.response.appendInt(this.direction);
        } else {
            this.response.appendString(this.wallLocation);
        }

        return this.response;
    }
}
