package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.habbo;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.installPlatform;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStore;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStores;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InMemoryChestStore;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionFailComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionSuccessComposer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * The official chest rules of Give Currency / Give Furni from chest: only upgraded chests of the
 * box's kind may be picked, a locked chest (or none at all) answers transaction failure 15, and the
 * chests come from the box's saved furni source rather than always from the picks.
 */
class WiredGiveFromChestRulesTest {
    private static final int CHEST_ID = 7;

    private static WiredSettings settings(int furniSource, int... furniIds) {
        WiredSettings settings = new WiredSettings(new int[] {0, 5, 0, -10, 0, 11}, "", furniIds, 0);
        settings.setVariableIds(new String[0]);
        settings.setFurniSourceTypes(new int[] {furniSource});
        settings.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_TRIGGER});
        return settings;
    }

    private static <T extends InteractionWiredChest> T chest(Class<T> kind, boolean upgraded, boolean locked) {
        T chest = mock(kind);
        ChestStorage contents = new ChestStorage();
        contents.setLocked(locked);
        when(chest.answersWired()).thenReturn(upgraded);
        when(chest.getContents()).thenReturn(contents);
        when(chest.getId()).thenReturn(CHEST_ID);
        return chest;
    }

    private static Room roomWith(Object item) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(1);
        if (item instanceof InteractionWiredChest chest)
            when(room.getHabboItem(CHEST_ID)).thenReturn(chest);
        return room;
    }

    private static void save(InteractionWiredEffect box, Room room, WiredSettings settings) throws Exception {
        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            installPlatform(platform, room);
            box.saveData(settings, null);
        }
    }

    private static int failureId(MessageComposer composer) {
        return ((WiredTransactionFailComposer) composer).getReason();
    }

    /** A real coin chest on a memory store, upgraded and unlocked, holding {@code coins}. */
    private static InteractionWiredChestCurrency realCoinChest(int coins) {
        com.eu.habbo.habbohotel.items.Item furni = base();
        com.eu.habbo.habbohotel.items.ItemInteraction interaction =
                mock(com.eu.habbo.habbohotel.items.ItemInteraction.class);
        org.mockito.Mockito.doReturn(InteractionWiredChestCurrency.class)
                .when(interaction)
                .getType();
        when(furni.getInteractionType()).thenReturn(interaction);
        InteractionWiredChestCurrency chest = new InteractionWiredChestCurrency(CHEST_ID, 1, furni, "", 0, 0);
        chest.getContents().enableWired();
        chest.getContents().setLocked(false);
        chest.addCoins(coins);
        return chest;
    }

    // --- 1. saves refuse normal chests -----------------------------------------------------------

    @Test
    void theCurrencyBoxRefusesANormalChestAndStaysAsItWas() throws Exception {
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);
        String before = box.getWiredData();
        Room room = roomWith(chest(InteractionWiredChestCurrency.class, false, false));

        WiredSaveException refused =
                assertThrows(WiredSaveException.class, () -> save(box, room, settings(100, CHEST_ID)));

        assertEquals("wiredfurni.error.require_coin_chests", refused.getMessage());
        assertEquals(before, box.getWiredData(), "a refused save changes nothing");
    }

    @Test
    void theCurrencyBoxRefusesAFurniChestEvenUpgraded() {
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);
        Room room = roomWith(chest(InteractionWiredChestFurni.class, true, false));

        WiredSaveException refused =
                assertThrows(WiredSaveException.class, () -> save(box, room, settings(100, CHEST_ID)));
        assertEquals("wiredfurni.error.require_coin_chests", refused.getMessage());
    }

    @Test
    void theFurniBoxRefusesANormalChestWithItsOwnKey() {
        WiredEffectGiveFurniFromChest box = new WiredEffectGiveFurniFromChest(1, 1, base(), "", 0, 0);
        Room room = roomWith(chest(InteractionWiredChestFurni.class, false, false));

        WiredSaveException refused =
                assertThrows(WiredSaveException.class, () -> save(box, room, settings(100, CHEST_ID)));
        assertEquals("wiredfurni.error.require_furni_chests", refused.getMessage());
    }

    @Test
    void anUpgradedChestOfTheRightKindSaves() throws Exception {
        WiredEffectGiveFurniFromChest box = new WiredEffectGiveFurniFromChest(1, 1, base(), "", 0, 0);
        Room room = roomWith(chest(InteractionWiredChestFurni.class, true, false));

        save(box, room, settings(100, CHEST_ID));

        assertEquals(CHEST_ID, json(box).get("chestIds").getAsJsonArray().get(0).getAsInt());
    }

    // --- 2. locked or missing chests answer failure 15 -------------------------------------------

    private static Habbo fire(InteractionWiredEffect box, Room room) {
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = habbo("Tester", unit);
        when(room.getHabbo(unit)).thenReturn(habbo);
        box.execute(context(room, unit));
        return habbo;
    }

    @Test
    void aLockedChestAnswersTheTriggeringUserWithFailure15() throws Exception {
        InteractionWiredChestCurrency chest = chest(InteractionWiredChestCurrency.class, true, true);
        chest.getContents().depositCurrency(-1, 50);
        Room room = roomWith(chest);
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);
        save(box, room, settings(100, CHEST_ID));

        Habbo habbo = fire(box, room);

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(habbo.getClient()).sendResponse(sent.capture());
        assertTrue(sent.getValue() instanceof WiredTransactionFailComposer);
        assertEquals(ChestTransactionFailures.NO_OR_LOCKED_CHESTS, failureId(sent.getValue()));
        assertEquals(50, chest.getContents().total(ChestStorage.KIND_CURRENCY), "nothing left the chest");
        verify(habbo, never()).giveCredits(anyInt());
    }

    @Test
    void noChestAtAllAnswersFailure15Too() throws Exception {
        Room room = roomWith(null);
        WiredEffectGiveFurniFromChest box = new WiredEffectGiveFurniFromChest(1, 1, base(), "", 0, 0);

        Habbo habbo = fire(box, room);

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(habbo.getClient()).sendResponse(sent.capture());
        assertEquals(ChestTransactionFailures.NO_OR_LOCKED_CHESTS, failureId(sent.getValue()));
    }

    @Test
    void anUnlockedUpgradedChestGivesAndAnnouncesTheReward() throws Exception {
        ChestStore previous = ChestStores.use(new InMemoryChestStore());
        try {
            InteractionWiredChestCurrency chest = realCoinChest(50);
            Room room = roomWith(chest);
            WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);
            save(box, room, settings(100, CHEST_ID));

            Habbo habbo = fire(box, room);

            verify(habbo).giveCredits(5);
            ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
            verify(habbo.getClient()).sendResponse(sent.capture());
            assertTrue(sent.getValue() instanceof WiredTransactionSuccessComposer, "AIR success type 2, no failure");
            assertEquals(
                    WiredTransactionSuccessComposer.TYPE_REWARD,
                    ((WiredTransactionSuccessComposer) sent.getValue()).getType());
            assertEquals(45, chest.getContents().getCoins());
        } finally {
            ChestStores.use(previous);
        }
    }

    // --- 3. the chests follow the saved furni source ---------------------------------------------

    @Test
    void theSavedFurniSourcesAreStoredAndSentBack() throws Exception {
        Room room = roomWith(chest(InteractionWiredChestCurrency.class, true, false));
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);
        WiredSettings settings = settings(200, CHEST_ID);
        settings.setFurniSourceTypes(new int[] {200, 201});
        settings.setUserSourceTypes(new int[] {0, 201});

        save(box, room, settings);
        assertArrayEquals(new int[] {200, 201}, box.officialFurniSources());
        assertArrayEquals(new int[] {0, 201}, box.officialUserSources());

        WiredEffectGiveCurrencyFromChest reloaded = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);
        reloaded.loadWiredData(row(box.getWiredData()), null);
        assertArrayEquals(new int[] {200, 201}, reloaded.officialFurniSources());
        assertArrayEquals(new int[] {0, 201}, reloaded.officialUserSources());
    }

    @Test
    void aRowFromBeforeTheSourcesKeepsUsingItsPickedChests() throws Exception {
        WiredEffectGiveFurniFromChest legacy = new WiredEffectGiveFurniFromChest(1, 1, base(), "", 0, 0);
        legacy.loadWiredData(row("{\"amount\":2,\"userSource\":0,\"delay\":0,\"chestIds\":[7]}"), null);

        assertArrayEquals(new int[] {100, 200}, legacy.officialFurniSources());
        assertArrayEquals(new int[] {0, 200}, legacy.officialUserSources());
    }

    @Test
    void aSelectorSourceDoesNotFallBackToThePicks() throws Exception {
        InteractionWiredChestCurrency chest = chest(InteractionWiredChestCurrency.class, true, false);
        chest.getContents().depositCurrency(-1, 50);
        Room room = roomWith(chest);
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);
        save(box, room, settings(WiredSourceUtil.SOURCE_SELECTOR, CHEST_ID));

        Habbo habbo = fire(box, room);

        // No selector ran, so no chest: the picked chest is not used behind the source's back.
        verify(habbo, never()).giveCredits(anyInt());
        assertEquals(50, chest.getContents().total(ChestStorage.KIND_CURRENCY));
    }
}
