package com.eu.habbo.habbohotel.rooms;

import java.util.List;

final class RoomPersistentStateFactory {

    private RoomPersistentStateFactory() {}

    static RoomPersistence.State capture(Room room) {
        return new RoomPersistence.State(
                room.getId(),
                room.getOwnerId(),
                room.getOwnerName(),
                room.getName(),
                room.getDescription(),
                room.getPassword(),
                room.getState(),
                room.getUsersMax(),
                room.getCategory(),
                room.getScore(),
                room.getFloorPaint(),
                room.getWallPaint(),
                room.getBackgroundPaint(),
                room.getWallSize(),
                room.getWallHeight(),
                room.getFloorSize(),
                List.copyOf(room.getMoodlightData().values()),
                room.getTags(),
                room.isAllowPets(),
                room.isAllowPetsEat(),
                room.isAllowWalkthrough(),
                room.isHideWall(),
                room.getChatMode(),
                room.getChatWeight(),
                room.getChatSpeed(),
                room.getChatDistance(),
                room.getChatProtection(),
                room.getMuteOption(),
                room.getKickOption(),
                room.getBanOption(),
                room.getPollId(),
                room.getGuildId(),
                room.getRollerSpeed(),
                room.hasCustomLayout(),
                room.isStaffPromotedRoom(),
                room.getPromotionManager().getPromotedFlag(),
                room.getTradeMode(),
                room.moveDiagonally(),
                room.isJukeboxActive(),
                room.isHideWired(),
                room.isAllowUnderpass(),
                room.isYoutubeEnabled(),
                room.isBuildersClubTrialLocked(),
                room.getBuildersClubOriginalState(),
                room.isMuteAllPets(),
                room.isLeaveOnDoorTileEnabled(),
                room.isIdleSleepEnabled(),
                room.getIdleSleepTimeoutSeconds(),
                room.isIdleAutokickEnabled(),
                room.getIdleAutokickTimeoutSeconds());
    }
}
