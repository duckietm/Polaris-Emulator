package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import polaris.hotel.wired.core.WiredSourceUtil;

class WiredConditionAvatarPayloadGuardTest {
    @Test
    void effectIdsSourcesAndQuantifiersAreBounded() {
        WiredConditionUserHasEffect condition = new WiredConditionUserHasEffect(1, 1, null, "", 0, 0);

        assertEquals(0, WiredUserConditionInputGuard.normalizeEffectId(-1));
        assertEquals(23, WiredUserConditionInputGuard.normalizeEffectId(23));
        assertEquals(
                WiredConditionUserHasEffect.MAX_EFFECT_ID,
                WiredUserConditionInputGuard.normalizeEffectId(Integer.MAX_VALUE));
        assertEquals(
                WiredSourceUtil.SOURCE_CLICKED_USER,
                WiredUserConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_CLICKED_USER));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredUserConditionInputGuard.normalizeUserSource(777));
        assertEquals(1, condition.normalizeQuantifier(1, 0));
        assertEquals(0, condition.normalizeQuantifier(5, 0));
    }

    @Test
    void handItemIdsSourcesAndQuantifiersAreBounded() {
        WiredConditionUserHasHandItem condition = new WiredConditionUserHasHandItem(1, 1, null, "", 0, 0);

        assertEquals(0, WiredUserConditionInputGuard.normalizeHandItemId(-1));
        assertEquals(9, WiredUserConditionInputGuard.normalizeHandItemId(9));
        assertEquals(
                WiredConditionUserHasHandItem.MAX_HAND_ITEM_ID,
                WiredUserConditionInputGuard.normalizeHandItemId(Integer.MAX_VALUE));
        assertEquals(
                WiredSourceUtil.SOURCE_SIGNAL,
                WiredUserConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredUserConditionInputGuard.normalizeUserSource(-44));
        assertEquals(1, condition.normalizeQuantifier(1));
        assertEquals(0, condition.normalizeQuantifier(8));
    }

    @Test
    void badgeCodesSourcesAndQuantifiersAreBounded() {
        WiredConditionUserWearsBadge condition = new WiredConditionUserWearsBadge(1, 1, null, "", 0, 0);

        assertEquals("", WiredUserConditionInputGuard.normalizeBadgeCode(null));
        assertEquals("ADM", WiredUserConditionInputGuard.normalizeBadgeCode(" ADM "));
        assertEquals(
                WiredConditionUserWearsBadge.MAX_BADGE_CODE_LENGTH,
                WiredUserConditionInputGuard.normalizeBadgeCode("x".repeat(200)).length());
        assertEquals(
                WiredSourceUtil.SOURCE_SELECTOR,
                WiredUserConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_SELECTOR));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredUserConditionInputGuard.normalizeUserSource(66));
        assertEquals(1, condition.normalizeQuantifier(1, 0));
        assertEquals(0, condition.normalizeQuantifier(3, 0));
    }
}
