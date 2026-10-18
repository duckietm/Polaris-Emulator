package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import org.junit.jupiter.api.Test;

/** The official time / date layouts: "use" flags and ranges, masks from bit 0. */
class WiredConditionTimeDateOfficialTest {

    @Test
    void timeUseFlagsAndRangesBecomeModes() {
        WiredConditionMatchTime time = new WiredConditionMatchTime(1, 1, null, "", 0, 0);
        // seconds off, minutes 30 exactly, hours 8-17
        time.saveData(new WiredSettings(new int[] {0, 1, 1, 0, 59, 30, 30, 8, 17}, "", new int[0], 0));

        String saved = time.getWiredData();
        assertTrue(saved.contains("\"secondMode\":0"), saved);
        assertTrue(saved.contains("\"minuteMode\":1") && saved.contains("\"minuteFrom\":30"), saved);
        assertTrue(
                saved.contains("\"hourMode\":2") && saved.contains("\"hourFrom\":8") && saved.contains("\"hourTo\":17"),
                saved);
    }

    @Test
    void dateMasksShiftByOneAndEmptyMeansAll() {
        WiredConditionMatchDate date = new WiredConditionMatchDate(1, 1, null, "", 0, 0);
        // Monday and Wednesday (bits 0 and 2), every month, days 5-10, no year
        date.saveData(new WiredSettings(new int[] {1, 0, 0b101, 5, 10, 0, 2026, 2026}, "", new int[0], 0));

        String saved = date.getWiredData();
        assertTrue(saved.contains("\"weekdayMask\":" + (0b1010)), saved);
        assertTrue(saved.contains("\"monthMask\":" + 0b1111111111110), saved);
        assertTrue(saved.contains("\"dayMode\":2") && saved.contains("\"yearMode\":0"), saved);
    }

    @Test
    void anInvertedYearRangeEndIsRaisedToItsStartButAnInvertedDayRangeIsKept() {
        WiredConditionMatchDate date = new WiredConditionMatchDate(1, 1, null, "", 0, 0);
        date.saveData(new WiredSettings(new int[] {1, 1, 0, 20, 10, 0, 9999, 1}, "", new int[0], 0));

        String saved = date.getWiredData();
        assertTrue(saved.contains("\"yearFrom\":9999") && saved.contains("\"yearTo\":9999"), saved);
        assertTrue(saved.contains("\"yearMode\":1"), saved);
        assertTrue(saved.contains("\"dayFrom\":20") && saved.contains("\"dayTo\":10"), saved);
    }
}
