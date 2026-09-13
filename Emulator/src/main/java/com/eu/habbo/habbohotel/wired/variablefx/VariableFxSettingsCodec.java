package com.eu.habbo.habbohotel.wired.variablefx;

/**
 * The only place that knows the Variable FX {@code intParams} layout. Longs occupy two slots,
 * high word first, exactly as the official editor writes them.
 */
public final class VariableFxSettingsCodec {
    public static final int INT_PARAM_COUNT = 21;

    private VariableFxSettingsCodec() {}

    public static VariableFxSettings read(int[] intParams, String[] variableIds, String stringParam) {
        if (intParams == null || intParams.length < INT_PARAM_COUNT) {
            throw new IllegalArgumentException("Variable FX settings need " + INT_PARAM_COUNT + " int params");
        }

        return new VariableFxSettings(
                intParams[0],
                intParams[1],
                intParams[2],
                intParams[3],
                intParams[4] != 0,
                intParams[5],
                intParams[6],
                intParams[7],
                intParams[8],
                intParams[9],
                readLong(intParams, 10),
                readLong(intParams, 12),
                intParams[14] != 0,
                intParams[15] != 0,
                intParams[16],
                intParams[17],
                readLong(intParams, 18),
                intParams[20],
                variableId(variableIds, 0),
                variableId(variableIds, 1),
                variableId(variableIds, 2),
                (stringParam != null) ? stringParam : "");
    }

    public static int[] write(VariableFxSettings settings) {
        int[] params = new int[INT_PARAM_COUNT];
        params[0] = settings.sourceType();
        params[1] = settings.visibility();
        params[2] = settings.showMode();
        params[3] = settings.showTriggerMask();
        params[4] = settings.showOnMouseHover() ? 1 : 0;
        params[5] = settings.showDuration();
        params[6] = settings.styleId();
        params[7] = settings.colorId();
        params[8] = settings.widthId();
        params[9] = settings.rendererId();
        writeLong(params, 10, settings.defaultMinValue());
        writeLong(params, 12, settings.defaultMaxValue());
        params[14] = settings.overrideMinEnabled() ? 1 : 0;
        params[15] = settings.overrideMaxEnabled() ? 1 : 0;
        params[16] = settings.overrideMinTarget();
        params[17] = settings.overrideMaxTarget();
        writeLong(params, 18, settings.audienceVariableValue());
        params[20] = settings.segments();
        return params;
    }

    private static long readLong(int[] params, int index) {
        return ((long) params[index] << 32) | (params[index + 1] & 0xFFFFFFFFL);
    }

    private static void writeLong(int[] params, int index, long value) {
        params[index] = (int) (value >> 32);
        params[index + 1] = (int) value;
    }

    private static String variableId(String[] variableIds, int index) {
        if (variableIds == null || index >= variableIds.length || variableIds[index] == null) {
            return "";
        }
        return variableIds[index];
    }
}
