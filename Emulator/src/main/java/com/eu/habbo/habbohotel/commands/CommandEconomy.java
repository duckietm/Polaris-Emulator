package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;

/**
 * Currency grants made by a staff command. They used to go through giveCredits/givePoints, which record the
 * receiving user as the actor with a generic economy.api reason - a :masscredits read in the economy audit log as a
 * thousand self-credits - and say nothing when the ledger refuses, so the command reported success anyway.
 */
final class CommandEconomy {

    private CommandEconomy() {}

    static String reason(String command) {
        return "command." + command;
    }

    static String operationId(String command, int staffId) {
        return EconomyOperationId.create(command + ":" + staffId);
    }

    /** Grants (or takes, when negative) credits on behalf of the staff member; true when it happened. */
    static boolean credits(Habbo target, int amount, String command, int staffId) {
        return target.applyCredits(amount, reason(command), operationId(command, staffId), staffId);
    }

    /** {@link #credits} for a user who may be offline. */
    static boolean credits(HabboManager habbos, int userId, int amount, String command, int staffId) {
        return habbos.applyCredits(userId, amount, reason(command), operationId(command, staffId), staffId);
    }

    /** The same for an activity-point currency (0 is duckets). */
    static boolean points(Habbo target, int type, int amount, String command, int staffId) {
        return target.applyPoints(type, amount, reason(command), operationId(command, staffId), staffId);
    }
}
