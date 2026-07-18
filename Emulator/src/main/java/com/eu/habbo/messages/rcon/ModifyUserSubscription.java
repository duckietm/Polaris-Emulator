package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModifyUserSubscription extends RCONMessage<ModifyUserSubscription.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyUserSubscription.class);
    static final int DEFAULT_MAX_DURATION_SECONDS = 31_536_000;

    public ModifyUserSubscription() {
        super(ModifyUserSubscription.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        try {

            if(json.user_id <= 0) {
                this.status = RCONMessage.HABBO_NOT_FOUND;
                this.message = "User not found";
                return;
            }

            if (!Emulator.getGameEnvironment().getSubscriptionManager().types.containsKey(json.type)) {
                this.status = RCONMessage.STATUS_ERROR;
                this.message = "%subscription% is not a valid subscription type".replace("%subscription%", json.type);
                return;
            }

            HabboInfo habbo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(json.user_id);

            if (habbo == null) {
                this.status = RCONMessage.HABBO_NOT_FOUND;
                this.message = "User not found";
                return;
            }

            int maxDuration = parseMaxDuration(Emulator.getConfig().getValue("rcon.subscription.max_duration_seconds", String.valueOf(DEFAULT_MAX_DURATION_SECONDS)));
            if (json.action.equalsIgnoreCase("add") || json.action.equalsIgnoreCase("+") || json.action.equalsIgnoreCase("a")) {
                if (!isValidDuration(json.duration, maxDuration)) {
                    this.status = RCONMessage.STATUS_ERROR;
                    this.message = "duration must be between 1 and " + maxDuration + " seconds";
                    return;
                }

                habbo.getHabboStats().createSubscription(json.type, json.duration);
                this.status = RCONMessage.STATUS_OK;
                this.message = "Successfully added %time% seconds to %subscription% on %user%".replace("%time%", json.duration + "").replace("%user%", habbo.getUsername()).replace("%subscription%", json.type);
            } else if (json.action.equalsIgnoreCase("remove") || json.action.equalsIgnoreCase("-") || json.action.equalsIgnoreCase("r")) {
                Subscription s = habbo.getHabboStats().getSubscription(json.type);

                if (s == null) {
                    this.status = RCONMessage.STATUS_ERROR;
                    this.message = "%user% does not have the %subscription% subscription".replace("%user%", habbo.getUsername()).replace("%subscription%", json.type);
                    return;
                }

                if (json.duration != -1) {
                    if (!isValidDuration(json.duration, maxDuration)) {
                        this.status = RCONMessage.STATUS_ERROR;
                        this.message = "duration must be between 1 and " + maxDuration + " seconds, or -1 to remove all time";
                        return;
                    }

                    s.addDuration(-Math.min(json.duration, s.getRemaining()));
                    this.status = RCONMessage.STATUS_OK;
                    this.message = "Successfully removed %time% seconds from %subscription% on %user%".replace("%time%", json.duration + "").replace("%user%", habbo.getUsername()).replace("%subscription%", json.type);
                } else {
                    s.addDuration(-s.getRemaining());
                    this.status = RCONMessage.STATUS_OK;
                    this.message = "Successfully removed %subscription% sub from %user%".replace("%user%", habbo.getUsername()).replace("%subscription%", json.type);
                }
            }
            else {
                this.status = RCONMessage.STATUS_ERROR;
                this.message = "Invalid action specified. Must be add, +, remove or -";
            }
        }
        catch (Exception e) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "Exception occurred";
            LOGGER.error("Exception occurred", e);
        }
    }

    static boolean isValidDuration(int duration, int maxDuration) {
        return duration >= 1 && duration <= maxDuration;
    }

    static int parseMaxDuration(String configured) {
        try {
            int parsed = Integer.parseInt(configured);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        return DEFAULT_MAX_DURATION_SECONDS;
    }

    static class JSON {

        @Positive(message = "invalid user")
        public int user_id;

        @NotBlank(message = "invalid subscription type")
        @Size(max = 64, message = "invalid subscription type")
        @Pattern(regexp = "[A-Za-z0-9_]+", message = "invalid subscription type")
        public String type = ""; // Subscription type e.g. HABBO_CLUB

        @NotBlank(message = "invalid action")
        @Size(max = 16, message = "invalid action")
        @Pattern(regexp = "(?i)^(add|remove|a|r|\\+|-)$", message = "invalid action")
        public String action = ""; // Can be add or remove

        public int duration = -1; // Time to add/remove in seconds. -1 means remove subscription entirely

    }
}
