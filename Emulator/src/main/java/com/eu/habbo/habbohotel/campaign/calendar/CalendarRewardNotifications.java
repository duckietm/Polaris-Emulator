package com.eu.habbo.habbohotel.campaign.calendar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bubbles a player gets for an opened calendar day: one for each part of the reward, sent as a
 * {@code calendar.reward.<kind>} notification ({@code credits}, {@code pixels}, {@code points}, {@code club},
 * {@code furni}) with the facts as keys ({@code amount}, {@code currency}, {@code subscription}, {@code furni}). The
 * client words it from its own texts ({@code notification.calendar.reward.<kind>.message}) and picks the icon. A badge
 * is left out: adding it already sends its own received-badge bubble.
 */
public final class CalendarRewardNotifications {

    public static final String TYPE_PREFIX = "calendar.reward.";

    /** One bubble: its notification type and keys. */
    public record Bubble(String type, Map<String, String> keys) {}

    private CalendarRewardNotifications() {}

    /**
     * The bubbles of a reward, in the order its parts are given.
     *
     * @param reward the reward of the day
     * @param pixelsGiven the duckets actually given (after the HC modifier)
     * @param itemClassName the classname of the furni given, or null
     */
    public static List<Bubble> bubbles(CalendarRewardObject reward, int pixelsGiven, String itemClassName) {
        List<Bubble> bubbles = new ArrayList<>();
        if (reward == null) return bubbles;

        if (reward.getCredits() > 0) bubbles.add(bubble("credits", reward.getCredits(), "-1"));
        if (pixelsGiven > 0) bubbles.add(bubble("pixels", pixelsGiven, "0"));
        if (reward.getPoints() > 0) {
            bubbles.add(bubble("points", reward.getPoints(), String.valueOf(reward.getPointsType())));
        }
        String subscription = reward.getSubscriptionType();
        if (subscription != null && !subscription.isEmpty() && reward.getSubscriptionDays() > 0) {
            Bubble club = bubble("club", reward.getSubscriptionDays(), null);
            club.keys().put("subscription", subscription);
            bubbles.add(club);
        }
        if (itemClassName != null && !itemClassName.isEmpty()) {
            Bubble furni = bubble("furni", 1, null);
            furni.keys().put("furni", itemClassName);
            bubbles.add(furni);
        }
        return bubbles;
    }

    private static Bubble bubble(String kind, int amount, String currency) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("display", "BUBBLE");
        keys.put("amount", String.valueOf(amount));
        if (currency != null) keys.put("currency", currency);
        return new Bubble(TYPE_PREFIX + kind, keys);
    }
}
