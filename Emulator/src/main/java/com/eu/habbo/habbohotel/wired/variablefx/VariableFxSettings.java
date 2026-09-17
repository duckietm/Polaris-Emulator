package com.eu.habbo.habbohotel.wired.variablefx;

/** The saved state of one Variable FX extra, in the order the official editor writes it. */
public record VariableFxSettings(
        int sourceType,
        int visibility,
        int showMode,
        int showTriggerMask,
        boolean showOnMouseHover,
        int showDuration,
        int styleId,
        int colorId,
        int widthId,
        int rendererId,
        long defaultMinValue,
        long defaultMaxValue,
        boolean overrideMinEnabled,
        boolean overrideMaxEnabled,
        int overrideMinTarget,
        int overrideMaxTarget,
        long audienceVariableValue,
        int segments,
        String overrideMinVariableId,
        String overrideMaxVariableId,
        String audienceVariableId,
        String icon) {

    public static final int SOURCE_USER = 0;
    public static final int SOURCE_FURNI = 1;
    public static final int SOURCE_GLOBAL = 2;

    public boolean isUserFx() {
        return sourceType == SOURCE_USER;
    }
}
