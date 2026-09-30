package com.eu.habbo.habbohotel.quests;

/** What a quest, a daily task or a reward-track task counts. */
public enum QuestGoalType {
    TALK_IN_ROOM("chat_with_someone"),
    VISIT_ROOMS("enter_other_users_room"),
    PLACE_FURNI("place_item"),
    GIVE_RESPECT("give_respect"),
    COMPLETE_QUEST("complete_quest"),
    CLAIM_DAILY_TASK("claim_daily_task"),
    /** Moved only by the reward-track wired boxes, never by play. */
    WIRED("wired"),

    // The remaining official Introduction track actions (reward_track.introduction.task.*).
    BUY_CATALOG_FURNI("buy_from_catalogue"),
    CHANGE_MOTTO("change_motto"),
    CHANGE_FIGURE("change_figure"),
    CLOSE_LOVE_LOCK("friend_furni_locked"),
    CREATE_ROOM("create_room"),
    DANCE("dance"),
    FEED_PET("pet_eat"),
    FOLLOW_FRIEND("follow_friend"),
    SWIM("swim"),
    GRAB_HAND_ITEM("find_hand_item"),
    LEVEL_PET("pet_level"),
    REQUEST_FRIEND("request_friend"),
    MOVE_FURNI("move_item"),
    ROTATE_FURNI("rotate_item"),
    RESPECT_PET("pet_respect"),
    PLACE_BUILDERS_CLUB_FURNI("place_builders_club_furni"),
    PUBLISH_PICTURE("publish_picture"),
    REPLENISH_RESPECT("replenish_respect"),
    SEND_MESSENGER_INVITE("send_messenger_invite"),
    SEND_MESSENGER_MESSAGE("send_messenger_message"),
    SET_RELATIONSHIP_STATUS("set_relationship_status"),
    USE_FURNI("switch_item_state"),
    USE_HABBICON("use_habbicon"),
    USE_TELEPORT("teleport"),
    WAVE("wave"),
    WEAR_BADGE("wear_badge");

    private final String actionType;

    QuestGoalType(String actionType) {
        this.actionType = actionType;
    }

    /** Accepts the enum name or the official reward-track action name (chat_with_someone, place_item...). */
    public static QuestGoalType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        for (QuestGoalType type : values()) {
            if (type.name().equalsIgnoreCase(normalized) || type.actionType.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }

    /** The official client action name, which picks the reward-track task icon. */
    public String actionType() {
        return this.actionType;
    }
}
