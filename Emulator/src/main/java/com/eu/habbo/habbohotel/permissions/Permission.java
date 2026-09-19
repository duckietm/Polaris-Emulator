package com.eu.habbo.habbohotel.permissions;

public class Permission {
    public static String ACC_ANYCHATCOLOR =
            "acc_anychatcolor"; // allows them to pick and choose any color from the chat bubbles.
    public static String ACC_ANYROOMOWNER = "acc_anyroomowner";
    public static String ACC_EMPTY_OTHERS = "acc_empty_others";
    public static String ACC_ENABLE_OTHERS = "acc_enable_others";
    public static String ACC_SEE_WHISPERS = "acc_see_whispers";
    public static String ACC_SEE_TENTCHAT = "acc_see_tentchat";
    public static String ACC_SUPERWIRED = "acc_superwired";
    public static String ACC_HOUSEKEEPING = "acc_housekeeping";
    public static String ACC_SUPPORTTOOL = "acc_supporttool";
    public static String ACC_UNKICKABLE = "acc_unkickable";
    public static String ACC_GUILDGATE = "acc_guildgate";
    public static String ACC_MOVEROTATE = "acc_moverotate";
    public static String ACC_PLACEFURNI = "acc_placefurni";
    public static String ACC_UNLIMITED_BOTS = "acc_unlimited_bots";
    public static String ACC_UNLIMITED_PETS = "acc_unlimited_pets";
    public static String ACC_HIDE_IP = "acc_hide_ip";
    public static String ACC_HIDE_MAIL = "acc_hide_mail";
    public static String ACC_NOT_MIMICED = "acc_not_mimiced";
    public static String ACC_CHAT_NO_FLOOD = "acc_chat_no_flood";
    public static String ACC_STAFF_PICK = "acc_staff_pick";
    public static String ACC_ENTERANYROOM = "acc_enteranyroom"; //
    public static String ACC_FULLROOMS = "acc_fullrooms";
    public static String ACC_INFINITE_CREDITS = "acc_infinite_credits";
    public static String ACC_INFINITE_PIXELS = "acc_infinite_pixels";
    public static String ACC_INFINITE_POINTS = "acc_infinite_points";
    public static String ACC_AMBASSADOR = "acc_ambassador";
    public static String ACC_CHAT_NO_LIMIT = "acc_chat_no_limit";
    public static String ACC_CHAT_NO_FILTER = "acc_chat_no_filter";
    public static String ACC_NOMUTE = "acc_nomute";
    public static String ACC_GUILD_ADMIN = "acc_guild_admin";
    public static String ACC_CATALOG_IDS = "acc_catalog_ids";
    public static String ACC_CATALOGFURNI = "acc_catalogfurni";
    public static String ACC_MODTOOL_TICKET_Q = "acc_modtool_ticket_q";
    public static String ACC_MODTOOL_USER_LOGS = "acc_modtool_user_logs";
    public static String ACC_MODTOOL_USER_ALERT = "acc_modtool_user_alert";
    public static String ACC_MODTOOL_USER_KICK = "acc_modtool_user_kick";
    public static String ACC_MODTOOL_USER_BAN = "acc_modtool_user_ban";
    public static String ACC_MODTOOL_ROOM_INFO = "acc_modtool_room_info";
    public static String ACC_MODTOOL_ROOM_LOGS = "acc_modtool_room_logs";
    public static String ACC_TRADE_ANYWHERE = "acc_trade_anywhere";
    public static String ACC_HELPER_USE_GUIDE_TOOL = "acc_helper_use_guide_tool";
    public static String ACC_HELPER_GIVE_GUIDE_TOURS = "acc_helper_give_guide_tours";
    public static String ACC_HELPER_JUDGE_CHAT_REVIEWS = "acc_helper_judge_chat_reviews";
    public static String ACC_FLOORPLAN_EDITOR = "acc_floorplan_editor";
    public static String ACC_UNIGNORABLE = "acc_unignorable";
    public static final String ACC_REWARDTRACK = "acc_rewardtrack";
    public final String key;
    public final PermissionSetting setting;

    public Permission(String key, PermissionSetting setting) {
        this.key = key;
        this.setting = setting;
    }
}
