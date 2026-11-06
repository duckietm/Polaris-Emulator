package polaris.hotel.wired;

/**
 * Wired condition codes as the official AIR client numbers them (ConditionCodes, build
 * WIN63-202610131318); the client picks a condition's editor by this code. Conditions the official
 * client does not have use 1000 and up, so they never land on an official one.
 */
public enum WiredConditionType {
    MATCH_SSHOT(0),
    FURNI_HAVE_USER(1),
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
    NOT_FURNI_HAVE_USER(14),
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
    MOVEMENT_VALIDATION(39), // i dont know what type it is but its needed
    COUNTER_TIME_MATCHES(35),
    USER_PERFORMS_ACTION(32),
    HAS_ALTITUDE(36),
    NOT_USER_PERFORMS_ACTION(33),
    NOT_ACTOR_HAS_HANDITEM(30),
    TRIGGERER_MATCH(26),
    NOT_TRIGGERER_MATCH(27),
    TEAM_HAS_SCORE(34),
    TEAM_HAS_RANK(31),
    MATCH_TIME(28),
    MATCH_DATE(29),
    ACTOR_DIR(37),
    SLC_QUANTITY(38),
    HAS_VAR(40),
    NOT_HAS_VAR(41),
    VAR_VAL_MATCH(42),
    VAR_AGE_MATCH(43),
    // Phase-2 chest/storage conditions. Require the matching Nitro WiredConditionLayoutCode.
    CHEST_HAS_ITEMS(45),
    CHEST_HAS_ITEM_TYPE(46),
    NO_BATTLEBANZAI(1000),
    USER_ON_FURNI_WITH_STATE(1001),
    TRG_FURNI_ADJACENT_STATE(1002),
    // A user-scoped condition whose answer comes from the user themselves — gender, room rights —
    // so the dialog offers the user source and the quantifier and nothing to type in. These used to
    // borrow ACTOR_WEARS_BADGE, which showed a badge-code field that was read and then ignored.
    USER_ATTRIBUTE(1003),
    NOT_USER_ATTRIBUTE(1004),
    // A threshold on something the user holds — inventory items, credits, diamonds, duckets. These
    // used to borrow TEAM_HAS_SCORE, whose dialog offered a team colour and a comparison operator that
    // the predicate never consulted; each of these boxes fixes its own direction.
    USER_AMOUNT(1005),
    // A boolean state the user is in that nothing types a value for — frozen, and whatever joins it.
    // These used to borrow ACTOR_WEARS_EFFECT, whose dialog asked for an effect id that the predicate
    // never read: the check is WiredFreezeUtil.isFrozen, not an effect comparison.
    USER_STATE(1006),
    NOT_USER_STATE(1007),
    // A piece of text read off the user that is not a badge code. These borrowed ACTOR_WEARS_BADGE,
    // whose field is genuinely used here — only mislabelled: a tag and a motto were both presented as
    // "Badge code", and with the badge's length limit rather than their own.
    USER_TAG(1008),
    NOT_USER_TAG(1009),
    USER_MOTTO(1010),
    // The three shapes that borrowed HAS_ALTITUDE. Its dialog gates furni selection to game counters,
    // which leaves the borrowers unable to pick the furni they are about; it also names the value an
    // altitude when it is a radius, offers furni sources where users are resolved, and carries a
    // comparison operator that the property checks never read.
    USER_RANGE(1011),
    FURNI_RANGE(1012),
    FURNI_PROPERTY(1013),
    // The official client has a USER_LEVEL condition, but its level is an account-wide one
    // Polaris does not keep. This reads the level the wired level-up add-on derives from a
    // variable instead, which is the only level a room actually owns. It takes 61 rather than
    // the first free code: 60 is where the array condition lands, and rather than have the two
    // collide on whichever merges second, this leaves that seat empty.
    USER_LEVEL(44),
    // Six shapes the hotel sells without a class behind them (2026-09-06 census). Each takes the
    // first free code; the client draws a dialog per code.
    USER_RANK(1015),
    FURNI_OPACITY(1016),
    USER_COOLDOWN(1017),
    USER_ONCE(1018),
    USER_DAILY(1019),
    USER_HIGHSCORE_POINTS(1020),
    CHECK_ARRAY(1014);

    public final int code;

    WiredConditionType(int code) {
        this.code = code;
    }
}
