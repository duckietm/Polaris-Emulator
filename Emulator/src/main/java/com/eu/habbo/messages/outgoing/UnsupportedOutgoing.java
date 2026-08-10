package com.eu.habbo.messages.outgoing;

/**
 * Message composers retained for source compatibility whose wire headers are not known for this revision.
 *
 * <p>These values must never be added to the active {@link Outgoing} registry.</p>
 */
public final class UnsupportedOutgoing {
    public static final int PublicRoomsComposer = -1;
    public static final int RemoveFriendComposer = -1;
    public static final int RoomEntryInfoComposer = -1;
    public static final int UserBCLimitsComposer = -1;
    public static final int QuestionInfoComposer = -1;
    public static final int UnknownGuildForumComposer6 = -1;
    public static final int UnknownGuildForumComposer7 = -1;
    public static final int RoomUserQuestionAnsweredComposer = -1;
    public static final int HotelViewCustomTimerComposer = -1;
    public static final int InventoryAddEffectComposer = -1;

    private UnsupportedOutgoing() {}
}
