package com.eu.habbo.habbohotel.navigation;

/**
 * One room of the navigator's "lifted" list (AIR LiftDataContainer, fed by the NavigatorLifted packet):
 * a promoted room shown with a teaser image. {@code image} is a path below the client's
 * {@code image.library.url}; empty means the client's default teaser. {@code areaId} is the navigator
 * area (public category) the room is promoted in, 0 for none.
 */
public final class NavigatorLiftedRoom {
    private final int roomId;
    private final int areaId;
    private final String image;
    private final String caption;

    public NavigatorLiftedRoom(int roomId, int areaId, String image, String caption) {
        this.roomId = roomId;
        this.areaId = areaId;
        this.image = image == null ? "" : image;
        this.caption = caption == null ? "" : caption;
    }

    public int getRoomId() {
        return this.roomId;
    }

    public int getAreaId() {
        return this.areaId;
    }

    public String getImage() {
        return this.image;
    }

    public String getCaption() {
        return this.caption;
    }
}
