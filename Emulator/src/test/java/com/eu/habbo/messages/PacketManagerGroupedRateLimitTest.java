package com.eu.habbo.messages;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.CatalogBuyItemAsGiftEvent;
import com.eu.habbo.messages.incoming.catalog.CatalogBuyItemEvent;
import com.eu.habbo.messages.incoming.catalog.PurchaseTargetOfferEvent;
import com.eu.habbo.messages.incoming.catalog.marketplace.BuyItemEvent;
import com.eu.habbo.messages.incoming.catalog.marketplace.SellItemEvent;
import com.eu.habbo.messages.incoming.catalog.marketplace.TakeBackItemEvent;
import com.eu.habbo.messages.incoming.earnings.ClaimAllEarningsRewardsEvent;
import com.eu.habbo.messages.incoming.earnings.ClaimEarningsRewardEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketManagerGroupedRateLimitTest {

    @Test
    void oneOperationBlocksOtherOperationsInTheSameGroupUntilItsOwnDeadline() {
        Map<String, Long> deadlines = new ConcurrentHashMap<>();

        assertTrue(acquire(deadlines, "catalog.purchase", 1_000, 10_000));
        assertFalse(acquire(deadlines, "catalog.purchase", 250, 10_500));
        assertTrue(acquire(deadlines, "catalog.purchase", 250, 11_000));
    }

    @Test
    void independentOperationGroupsDoNotBlockEachOther() {
        Map<String, Long> deadlines = new ConcurrentHashMap<>();

        assertTrue(acquire(deadlines, "catalog.purchase", 1_000, 10_000));
        assertTrue(acquire(deadlines, "earnings.claim", 1_000, 10_000));
    }

    @Test
    void relatedEconomyHandlersDeclareSharedGroups() {
        assertGroup("catalog.purchase", new CatalogBuyItemEvent(), new CatalogBuyItemAsGiftEvent(), new PurchaseTargetOfferEvent());
        assertGroup("marketplace.mutation", new BuyItemEvent(), new SellItemEvent(), new TakeBackItemEvent());
        assertGroup("earnings.claim", new ClaimAllEarningsRewardsEvent(), new ClaimEarningsRewardEvent());
    }

    private static boolean acquire(Map<String, Long> deadlines, String group, long cooldownMs, long nowMs) {
        Method method = assertDoesNotThrow(
                () -> PacketManager.class.getDeclaredMethod("acquireGroupedRateLimit", Map.class, String.class, long.class, long.class),
                "PacketManager must expose the grouped rate-limit gate");
        method.setAccessible(true);
        return assertDoesNotThrow(() -> (boolean) method.invoke(null, deadlines, group, cooldownMs, nowMs));
    }

    private static void assertGroup(String expected, MessageHandler... handlers) {
        Method method = assertDoesNotThrow(
                () -> MessageHandler.class.getMethod("getRatelimitGroup"),
                "MessageHandler must expose an optional shared rate-limit group");

        for (MessageHandler handler : handlers) {
            assertEquals(expected, assertDoesNotThrow(() -> method.invoke(handler)));
        }
    }
}
