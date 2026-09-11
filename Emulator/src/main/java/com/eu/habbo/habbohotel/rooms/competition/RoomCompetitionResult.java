package com.eu.habbo.habbohotel.rooms.competition;

/**
 * The result codes the official client reads out of the submit and the voting packet. They pick the
 * texts the window shows and which button it offers, so they are not ours to renumber: the names
 * come from `roomcompetition.caption.submit.<n>` and `roomcompetition.caption.vote.<n>`.
 */
public final class RoomCompetitionResult {
    /** Not a code the client knows: there is no window to show in this room at all. */
    public static final int NOTHING = -1;

    /** "Your room is now part of the competition!" — the entry is in. */
    public static final int SUBMITTED = 0;

    /** The room qualifies; the button submits it. */
    public static final int READY = 1;

    /** "Are you sure you want to submit?" — the button confirms. */
    public static final int CONFIRM = 2;

    /** The room is missing furniture the competition requires; the window lists it. */
    public static final int MISSING_FURNI = 3;

    /** "Your room door is not open" — visitors could not get in to vote. */
    public static final int DOOR_CLOSED = 4;

    /** The room cannot enter; the button opens the navigator to pick another one. */
    public static final int ROOM_NOT_ELIGIBLE = 5;

    /** First contact: the rules, and a button that accepts them. */
    public static final int RULES = 6;

    /**
     * Voting: the visitor may vote, and the votes they have left say whether the button is offered.
     * The renderer names these three (CompetitionVotingInfoResult): anything but zero is a visitor
     * who is not eligible at all, which is a rule this hotel does not have yet.
     */
    public static final int VOTE_ALLOWED = 0;

    /** Voting: the visitor lacks a talent-track perk the competition asks for. */
    public static final int VOTE_PERK_MISSING = 1;

    /** Voting: the visitor lacks a badge the competition asks for. */
    public static final int VOTE_BADGE_MISSING = 2;

    private RoomCompetitionResult() {}
}
