package com.eu.habbo.messages.incoming.housekeeping;

final class HousekeepingRankPolicy {
    private HousekeepingRankPolicy() {}

    static Decision evaluate(int operatorRankId, int targetRankId, int highestConfiguredRankId) {
        if (operatorRankId <= 0 || targetRankId <= 0 || highestConfiguredRankId <= 0) {
            return new Decision(false, Reason.INVALID_RANK_CONTEXT);
        }

        if (targetRankId < operatorRankId) {
            return new Decision(true, Reason.ALLOWED_LOWER_RANK);
        }

        if (operatorRankId >= highestConfiguredRankId && targetRankId <= operatorRankId) {
            return new Decision(true, Reason.ALLOWED_CORE_PEER);
        }

        return new Decision(false, Reason.TARGET_NOT_LOWER);
    }

    enum Reason {
        ALLOWED_LOWER_RANK,
        ALLOWED_CORE_PEER,
        INVALID_RANK_CONTEXT,
        TARGET_NOT_LOWER
    }

    record Decision(boolean allowed, Reason reason) {}
}
