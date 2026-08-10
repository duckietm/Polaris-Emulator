package com.eu.habbo.messages.incoming;

/**
 * Packet handlers retained for source compatibility whose wire headers are not known for this revision.
 *
 * <p>These values must never be added to the active {@link Incoming} registry.</p>
 */
public final class UnsupportedIncoming {
    public static final int ModToolWarnEvent = -1; // Historical candidate: 3763
    public static final int ModToolBanEvent = -1;
    public static final int SearchRoomsByTagEvent = -1; // Historical candidate: 1956
    public static final int ModToolRequestRoomUserChatlogEvent = -1;
    public static final int RequestAchievementConfigurationEvent = -1;
    public static final int HotelViewClaimBadgeRewardEvent = -1;

    private UnsupportedIncoming() {}
}
