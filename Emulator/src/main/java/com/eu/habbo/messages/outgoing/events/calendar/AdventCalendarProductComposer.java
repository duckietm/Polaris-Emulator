package com.eu.habbo.messages.outgoing.events.calendar;

import com.eu.habbo.habbohotel.campaign.calendar.CalendarManager;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardObject;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AdventCalendarProductComposer extends MessageComposer {
    public final boolean visible;
    public final CalendarRewardObject rewardObject;
    public final Habbo habbo;

    public AdventCalendarProductComposer(boolean visible, CalendarRewardObject rewardObject, Habbo habbo) {
        this.visible = visible;
        this.rewardObject = rewardObject;
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AdventCalendarProductComposer);
        this.response.appendBoolean(this.visible);

        appendReward(this.response, this.rewardObject, this.habbo);

        return this.response;
    }

    /**
     * The reward part of the packet (product name, custom image, furni classname) and, after it, the Octane extension
     * with what the reward holds. Also written per opened day by {@link AdventCalendarDataComposer}.
     */
    public static void appendReward(ServerMessage response, CalendarRewardObject reward, Habbo habbo) {
        double pixelModifier =
                habbo != null && habbo.getHabboStats().hasActiveClub() ? CalendarManager.HC_MODIFIER : 1.0;
        String className = "";
        String productName = reward.getProductName()
                .replace("%credits%", String.valueOf(reward.getCredits()))
                .replace("%pixels%", String.valueOf((int) (reward.getPixels() * (pixelModifier))))
                .replace("%points%", String.valueOf(reward.getPoints()))
                .replace("%points_type%", String.valueOf(reward.getPointsType()))
                .replace("%badge%", reward.getBadge());
        if (reward.getSubscriptionType() != null) {
            productName = productName
                    .replace("%subscription_type%", reward.getSubscriptionType())
                    .replace("%subscription_days%", String.valueOf(reward.getSubscriptionDays()));
        }

        if (reward.getItem() != null) {
            productName = productName.replace("%item%", reward.getItem().getName());
            className = reward.getItem().getName();
        }

        response.appendString(productName);
        response.appendString(reward.getCustomImage());
        response.appendString(className);
        // Octane extension, read only when present: what the reward holds, so the opened box can show its picture
        // for a badge, a subscription or a currency as well as for a furni.
        response.appendString(reward.getBadge() == null ? "" : reward.getBadge());
        response.appendInt(reward.getCredits());
        response.appendInt((int) (reward.getPixels() * (pixelModifier)));
        response.appendInt(reward.getPoints());
        response.appendInt(reward.getPointsType());
        response.appendString(reward.getSubscriptionType() == null ? "" : reward.getSubscriptionType());
        response.appendInt(reward.getSubscriptionDays());
    }

    public boolean isVisible() {
        return visible;
    }

    public CalendarRewardObject getRewardObject() {
        return rewardObject;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
