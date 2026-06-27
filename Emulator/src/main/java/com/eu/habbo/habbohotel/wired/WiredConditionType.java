package com.eu.habbo.habbohotel.wired;

public enum WiredConditionType {
    MATCH_SSHOT(0),
    FURNI_HAVE_HABBO(1),
    TRIGGER_ON_FURNI(2),
    TIME_MORE_THAN(3),
    TIME_LESS_THAN(4),
    USER_COUNT(5),
    ACTOR_IN_TEAM(6),
    FURNI_HAS_FURNI(7),
    STUFF_IS(8),
    ACTOR_IN_GROUP(10),
    ACTOR_WEARS_BADGE(11),
    ACTOR_WEARS_EFFECT(12),
    NOT_MATCH_SSHOT(13),
    NOT_FURNI_HAVE_HABBO(14),
    NOT_ACTOR_ON_FURNI(15),
    NOT_USER_COUNT(16),
    NOT_ACTOR_IN_TEAM(17),
    NOT_FURNI_HAVE_FURNI(18),
    NOT_STUFF_IS(19),
    NOT_ACTOR_IN_GROUP(21),
    NOT_ACTOR_WEARS_BADGE(22),
    NOT_ACTOR_WEARS_EFFECT(23),
    DATE_RANGE(24),
    ACTOR_HAS_HANDITEM(25),
    MOVEMENT_VALIDATION(26), // i dont know what type it is but its needed
    COUNTER_TIME_MATCHES(27),
    USER_PERFORMS_ACTION(28),
    HAS_ALTITUDE(29),
    NOT_USER_PERFORMS_ACTION(30),
    NOT_ACTOR_HAS_HANDITEM(31),
    TRIGGERER_MATCH(32),
    NOT_TRIGGERER_MATCH(33),
    TEAM_HAS_SCORE(34),
    TEAM_HAS_RANK(35),
    MATCH_TIME(36),
    MATCH_DATE(37),
    ACTOR_DIR(38),
    SLC_QUANTITY(39),
    HAS_VAR(40),
    NOT_HAS_VAR(41),
    VAR_VAL_MATCH(42),
    VAR_AGE_MATCH(43),
    // New client dialog (no-input condition). Requires the matching Nitro WiredConditionLayoutCode.
    NO_BATTLEBANZAI(44),
    USER_ON_FURNI_WITH_STATE(45),
    TRG_FURNI_ADJACENT_STATE(46),
    // Phase-2 chest/storage condition. Requires the matching Nitro WiredConditionLayoutCode.
    CHEST_HAS_ITEMS(47);

    public final int code;

    WiredConditionType(int code) {
        this.code = code;
    }
}
