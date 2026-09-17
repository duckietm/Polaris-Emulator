package com.eu.habbo.habbohotel.wired.variablefx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VariableFxSettingsCodecTest {

    private static final int[] CAPTURED = {1, 0, 1, 6, 1, 30, 2, 3, 1, 0, 0, 0, 0, 100, 1, 0, 0, 1, 0, 5, 4};

    @Test
    void readsEveryFieldFromTheCapturedLayout() {
        VariableFxSettings settings =
                VariableFxSettingsCodec.read(CAPTURED, new String[] {"minVar", "maxVar", "audVar"}, "");

        assertEquals(1, settings.sourceType());
        assertEquals(0, settings.visibility());
        assertEquals(1, settings.showMode());
        assertEquals(6, settings.showTriggerMask());
        assertEquals(true, settings.showOnMouseHover());
        assertEquals(30, settings.showDuration());
        assertEquals(2, settings.styleId());
        assertEquals(3, settings.colorId());
        assertEquals(1, settings.widthId());
        assertEquals(0, settings.rendererId());
        assertEquals(0L, settings.defaultMinValue());
        assertEquals(100L, settings.defaultMaxValue());
        assertEquals(true, settings.overrideMinEnabled());
        assertEquals(false, settings.overrideMaxEnabled());
        assertEquals(0, settings.overrideMinTarget());
        assertEquals(1, settings.overrideMaxTarget());
        assertEquals(5L, settings.audienceVariableValue());
        assertEquals(4, settings.segments());
        assertEquals("minVar", settings.overrideMinVariableId());
        assertEquals("maxVar", settings.overrideMaxVariableId());
        assertEquals("audVar", settings.audienceVariableId());
    }

    @Test
    void writesBackTheSameVector() {
        VariableFxSettings settings = VariableFxSettingsCodec.read(CAPTURED, new String[0], "");

        assertArrayEquals(CAPTURED, VariableFxSettingsCodec.write(settings));
    }

    @Test
    void splitsLongValuesAcrossTwoIntsHighWordFirst() {
        int[] params = CAPTURED.clone();
        params[12] = 1;
        params[13] = 0;

        VariableFxSettings settings = VariableFxSettingsCodec.read(params, new String[0], "");

        assertEquals(4294967296L, settings.defaultMaxValue());
        assertArrayEquals(params, VariableFxSettingsCodec.write(settings));
    }

    @Test
    void rejectsAShortVector() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VariableFxSettingsCodec.read(new int[] {1, 2, 3}, new String[0], ""));
    }

    @Test
    void treatsMissingVariableIdsAsEmptyStrings() {
        VariableFxSettings settings = VariableFxSettingsCodec.read(CAPTURED, new String[0], "");

        assertEquals("", settings.overrideMinVariableId());
        assertEquals("", settings.overrideMaxVariableId());
        assertEquals("", settings.audienceVariableId());
    }
}
