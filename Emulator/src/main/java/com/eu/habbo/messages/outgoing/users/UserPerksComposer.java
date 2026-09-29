package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * PerkAllowances: which client features the user may use. Each perk reads a permission key, so a
 * hotel can switch one off per rank. A key the rank does not define keeps the perk's default.
 */
public class UserPerksComposer extends MessageComposer {
    /** A perk, its refusal text, the key that controls it and its value when the key is not defined. */
    record Perk(String code, String refusal, String permission, boolean defaultAllowed) {}

    static final String TRADE = "TRADE";

    static final List<Perk> PERKS = List.of(
            new Perk(
                    "USE_GUIDE_TOOL",
                    "requirement.unfulfilled.helper_level_4",
                    Permission.ACC_HELPER_USE_GUIDE_TOOL,
                    false),
            new Perk("GIVE_GUIDE_TOURS", "", "acc_helper_give_guide_tours", false),
            new Perk(
                    "JUDGE_CHAT_REVIEWS",
                    "requirement.unfulfilled.helper_level_6",
                    "acc_helper_judge_chat_reviews",
                    false),
            new Perk(
                    "VOTE_IN_COMPETITIONS",
                    "requirement.unfulfilled.helper_level_2",
                    "acc_perk_vote_in_competitions",
                    true),
            new Perk("CALL_ON_HELPERS", "", "acc_perk_call_on_helpers", true),
            new Perk("CITIZEN", "", "acc_perk_citizen", true),
            new Perk(TRADE, "requirement.unfulfilled.no_trade_lock", null, true),
            new Perk(
                    "HEIGHTMAP_EDITOR_BETA",
                    "requirement.unfulfilled.feature_disabled",
                    Permission.ACC_FLOORPLAN_EDITOR,
                    false),
            new Perk("BUILDER_AT_WORK", "", "acc_perk_builder_at_work", true),
            new Perk("CAMERA", "", "acc_camera", false),
            new Perk("NAVIGATOR_PHASE_TWO_2014", "", "acc_perk_navigator_phase_two", true),
            new Perk("MOUSE_ZOOM", "", "acc_perk_mouse_zoom", true),
            new Perk("NAVIGATOR_ROOM_THUMBNAIL_CAMERA", "", "acc_perk_navigator_thumbnail_camera", true),
            new Perk("HABBO_CLUB_OFFER_BETA", "", "acc_perk_habbo_club_offer_beta", true));

    private final Habbo habbo;

    public UserPerksComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        PermissionsManager permissions = Emulator.getGameEnvironment().getPermissionsManager();

        this.response.init(Outgoing.UserPerksComposer);
        this.response.appendInt(PERKS.size());

        for (Perk perk : PERKS) {
            this.response.appendString(perk.code());
            this.response.appendString(perk.refusal());
            this.response.appendBoolean(this.isAllowed(perk, permissions));
        }

        return this.response;
    }

    private boolean isAllowed(Perk perk, PermissionsManager permissions) {
        if (TRADE.equals(perk.code())) {
            return this.habbo.getHabboStats().allowTrade();
        }

        // A user's own value decides, also for a perk the rank does not define.
        PermissionSetting override =
                permissions.getOverrides().find(this.habbo.getHabboInfo().getId(), perk.permission());
        if (override != null) {
            return override != PermissionSetting.DISALLOWED;
        }

        return isAllowed(
                perk, this.habbo.getHabboInfo().getRank(), permissions.hasPermission(this.habbo, perk.permission()));
    }

    /** Held, or not defined for the rank and on by default. */
    static boolean isAllowed(Perk perk, Rank rank, boolean held) {
        if (held) {
            return true;
        }

        boolean defined = rank != null && rank.getPermissions().containsKey(perk.permission());
        return !defined && perk.defaultAllowed();
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
