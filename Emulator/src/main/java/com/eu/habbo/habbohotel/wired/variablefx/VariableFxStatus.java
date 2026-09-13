package com.eu.habbo.habbohotel.wired.variablefx;

import java.util.Map;

/** One variable's live value for one entity, as the status packet carries it. */
public record VariableFxStatus(
        String statusKey,
        boolean isInitialize,
        boolean isUserEntity,
        int entityId,
        long value,
        boolean hasOverrides,
        long overrideMinValue,
        long overrideMaxValue,
        Map<String, String> extras) {

    public VariableFxStatus {
        extras = (extras != null) ? Map.copyOf(extras) : Map.of();
    }

    /** The key the client splits on the pipe: configuration id, then variable id. */
    public static String key(int configId, String variableId) {
        return configId + "|" + variableId;
    }
}
