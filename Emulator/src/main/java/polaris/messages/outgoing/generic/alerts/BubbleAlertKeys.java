package polaris.messages.outgoing.generic.alerts;

public enum BubbleAlertKeys {
    ADMIN_PERSISTENT("admin.persistent"),
    ADMIN_TRANSIENT("admin.transient"),
    BUILDERS_CLUB_MEMBERSHIP_EXPIRED("builders_club.membership_expired"),
    BUILDERS_CLUB_MEMBERSHIP_EXPIRES("builders_club.membership_expires"),
    BUILDERS_CLUB_MEMBERSHIP_EXTENDED("builders_club.membership_extended"),
    BUILDERS_CLUB_MEMBERSHIP_MADE("builders_club.membership_made"),
    BUILDERS_CLUB_MEMBERSHIP_RENEWED("builders_club.membership_renewed"),
    BUILDERS_CLUB_ROOM_LOCKED("builders_club.room_locked"),
    BUILDERS_CLUB_ROOM_UNLOCKED("builders_club.room_unlocked"),
    BUILDERS_CLUB_VISIT_DENIED_OWNER("builders_club.visit_denied_for_owner"),
    BUILDERS_CLUB_VISIT_DENIED_GUEST("builders_club.visit_denied_for_visitor"),
    CASINO_TOO_MANY_DICE_PLACEMENT("casino.too_many_dice.placement"),
    CASINO_TOO_MANY_DICE("casino.too_many_dice"),
    FLOORPLAN_EDITOR_ERROR("floorplan_editor.error"),
    FORUMS_DELIVERED("forums.delivered"),
    FORUMS_FORUM_SETTINGS_UPDATED("forums.forum_settings_updated"),
    FORUMS_MESSAGE_HIDDEN("forums.message.hidden"),
    FORUMS_MESSAGE_RESTORED("forums.message.restored"),
    FORUMS_THREAD_HIDDEN("forums.thread.hidden"),
    FORUMS_ACCESS_DENIED("forums.error.access_denied"),
    FORUMS_THREAD_LOCKED("forums.thread.locked"),
    FORUMS_THREAD_PINNED("forums.thread.pinned"),
    FORUMS_THREAD_RESTORED("forums.thread.restored"),
    FORUMS_THREAD_UNLOCKED("forums.thread.unlocked"),
    FORUMS_THREAD_UNPINNED("forums.thread.unpinned"),
    FURNITURE_PLACEMENT_ERROR("furni_placement_error"),
    GIFTING_VALENTINE("gifting.valentine"),
    NUX_POPUP("nux.popup"),
    PURCHASING_ROOM("purchasing.room"),
    RECEIVED_GIFT("received.gift"),
    RECEIVED_BADGE("received.badge"),
    FIGURESET_REDEEMED("figureset.redeemed.success"),
    FIGURESET_OWNED_ALREADY("figureset.already.redeemed");

    public final String key;

    BubbleAlertKeys(String key) {
        this.key = key;
    }
}
