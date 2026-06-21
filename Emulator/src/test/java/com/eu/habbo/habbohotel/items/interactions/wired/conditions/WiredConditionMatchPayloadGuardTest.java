package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WiredConditionMatchPayloadGuardTest {
    @Test
    void matchStateNormalizesSourcesQuantifierAndSettings() {
        WiredConditionMatchStatePosition condition = new WiredConditionMatchStatePosition(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SELECTED, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(9090));
        assertEquals(1, condition.normalizeQuantifier(1));
        assertEquals(0, condition.normalizeQuantifier(9));

        WiredMatchFurniSetting normalized = condition.normalizeSetting(new WiredMatchFurniSetting(5, null, 99, -10, -20, Room.MAXIMUM_FURNI_HEIGHT + 100));

        assertNotNull(normalized);
        assertEquals(5, normalized.item_id);
        assertEquals("", normalized.state);
        assertEquals(7, normalized.rotation);
        assertEquals(0, normalized.x);
        assertEquals(0, normalized.y);
        assertEquals(Room.MAXIMUM_FURNI_HEIGHT, normalized.z);
        assertNull(condition.normalizeSetting(new WiredMatchFurniSetting(0, "", 0, 0, 0, 0)));
    }

    @Test
    void matchStateParsesLegacySettingsSafely() {
        WiredConditionMatchStatePosition condition = new WiredConditionMatchStatePosition(1, 1, null, "", 0, 0);

        assertNotNull(condition.parseLegacySetting(new String[]{"7", "1", "2", "3", "4", "5"}));
        assertNull(condition.parseLegacySetting(new String[]{"bad", "1", "2", "3", "4"}));
        assertNull(condition.parseLegacySetting(new String[]{"7", "1"}));
    }

    @Test
    void furniTypeMatchBoundsSourcesAndParsesIds() {
        WiredConditionFurniTypeMatch condition = new WiredConditionFurniTypeMatch(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(WiredConditionFurniTypeMatch.SOURCE_SECONDARY_SELECTED, condition.normalizeFurniSource(WiredConditionFurniTypeMatch.SOURCE_SECONDARY_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(-77));
        assertEquals(1, condition.normalizeQuantifier(1));
        assertEquals(0, condition.normalizeQuantifier(6));
        assertEquals(List.of(1, 2, 3), condition.parseIds("1;bad,2\t3"));
    }
}
