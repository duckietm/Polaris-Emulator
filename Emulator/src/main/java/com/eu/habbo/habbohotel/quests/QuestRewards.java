package com.eu.habbo.habbohotel.quests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hands out the currency, badge and furni rewards of quests, daily tasks and reward-track prizes. */
public final class QuestRewards {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestRewards.class);

    public static final String TYPE_CREDITS = "credits";
    public static final String TYPE_DUCKETS = "duckets";
    public static final String TYPE_DIAMONDS = "diamonds";
    public static final String TYPE_BADGE = "badge";

    /** A furni: the extra parameter is the items_base name, the amount is how many. */
    public static final String TYPE_FURNI = "furni";

    /** How many copies one prize may hand out at once. */
    public static final int MAX_FURNI_PER_PRIZE = 10;

    public static final int DIAMONDS_POINT_TYPE = 5;

    private QuestRewards() {}

    /** The official rule: HC members earn double duckets from quests. */
    public static int ducketAmount(int amount, boolean hasClub) {
        return hasClub ? amount * 2 : amount;
    }

    /** activityPointType style rewards: -1 credits, otherwise the points type (0 duckets, 5 diamonds). */
    public static void grantActivityPoints(Habbo habbo, int activityPointType, int amount) {
        if (habbo == null || amount < 1) {
            return;
        }
        if (activityPointType == Quest.REWARD_CREDITS) {
            habbo.giveCredits(amount, "quests.reward");
            return;
        }
        int granted = activityPointType == Quest.REWARD_DUCKETS
                ? ducketAmount(amount, habbo.getHabboStats().hasActiveClub())
                : amount;
        habbo.givePoints(activityPointType, granted, "quests.reward");
    }

    /** String reward types used by the daily tasks and the reward track. */
    public static void grantTyped(Habbo habbo, String rewardType, String extra, int amount) {
        if (habbo == null || rewardType == null) {
            return;
        }
        switch (rewardType.trim().toLowerCase()) {
            case TYPE_CREDITS -> grantActivityPoints(habbo, Quest.REWARD_CREDITS, amount);
            case TYPE_DUCKETS -> grantActivityPoints(habbo, Quest.REWARD_DUCKETS, amount);
            case TYPE_DIAMONDS -> grantActivityPoints(habbo, DIAMONDS_POINT_TYPE, amount);
            case TYPE_BADGE -> grantBadge(habbo, extra);
            case TYPE_FURNI -> grantFurni(habbo, extra, amount);
            default -> LOGGER.warn("Unknown quest reward type {}", rewardType);
        }
    }

    /** Puts {@code amount} copies of the furni named {@code itemName} in the user's hand. */
    public static void grantFurni(Habbo habbo, String itemName, int amount) {
        if (habbo == null || habbo.getClient() == null) {
            return;
        }
        Item item = Emulator.getGameEnvironment().getItemManager().getItem(itemName);
        if (item == null) {
            LOGGER.warn("Quest reward furni {} does not exist", itemName);
            return;
        }
        int copies = Math.max(1, Math.min(amount, MAX_FURNI_PER_PRIZE));
        for (int i = 0; i < copies; i++) {
            HabboItem habboItem = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createItem(habbo.getHabboInfo().getId(), item, 0, 0, "");
            if (habboItem == null) {
                continue;
            }
            habbo.getInventory().getItemsComponent().addItem(habboItem);
            habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
        }
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }

    /** Maps the string reward type to the client activityPointType, -2 when it is not a currency. */
    public static int activityPointTypeOf(String rewardType) {
        if (rewardType == null) {
            return -2;
        }
        return switch (rewardType.trim().toLowerCase()) {
            case TYPE_CREDITS -> Quest.REWARD_CREDITS;
            case TYPE_DUCKETS -> Quest.REWARD_DUCKETS;
            case TYPE_DIAMONDS -> DIAMONDS_POINT_TYPE;
            default -> -2;
        };
    }

    public static void grantBadge(Habbo habbo, String badgeCode) {
        if (habbo == null || badgeCode == null || badgeCode.isBlank()) {
            return;
        }
        if (habbo.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
            return;
        }
        HabboBadge badge = new HabboBadge(0, badgeCode, 0, habbo);
        badge.run();
        habbo.getInventory().getBadgesComponent().addBadge(badge);
        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
        }
    }
}
