package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionGate;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractPayment;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractReward;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractTrade;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredCustomContract;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ItemInteractionRegistryCompatibilityTest {

    @Test
    void lookupFallbackOrderingAndUniquenessStayStable() {
        TestItemManager manager = new TestItemManager();
        manager.loadDefaults();

        ItemInteraction defaultInteraction = manager.getItemInteraction(InteractionDefault.class);
        assertSame(defaultInteraction, manager.getItemInteraction("missing-interaction"));

        List<String> names = manager.getInteractionList();
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        assertEquals(sorted, names);
        assertTrue(names.contains("default"));

        assertThrows(
                RuntimeException.class,
                () -> manager.addItemInteraction(
                        new ItemInteraction("duplicate-default-type", InteractionDefault.class)));
    }

    @Test
    void duplicateBuiltInNamesResolveDeterministicallyToLastRegistration() {
        TestItemManager manager = new TestItemManager();
        manager.loadDefaults();

        assertSame(
                InteractionWiredContractPayment.class,
                manager.getItemInteraction("wf_contract_payment").getType());
        assertSame(
                InteractionWiredContractReward.class,
                manager.getItemInteraction("WF_CONTRACT_REWARD").getType());
        assertSame(
                InteractionWiredContractTrade.class,
                manager.getItemInteraction("wf_contract_trade").getType());
        assertSame(
                InteractionWiredCustomContract.class,
                manager.getItemInteraction("wf_xtra_custom_contract").getType());

        List<String> names = manager.getInteractionList();
        assertEquals(
                names.size(),
                new HashSet<>(names.stream()
                                .map(name -> name.toLowerCase(Locale.ROOT))
                                .toList())
                        .size(),
                "runtime interaction names must be unique");
    }

    @Test
    void duplicateBuiltInNamesFailFastWithoutChangingTheExistingAliases() {
        ItemInteractionRegistry registry = new ItemInteractionRegistry();
        ItemInteraction first = new ItemInteraction("first", InteractionDefault.class);
        ItemInteraction replacement = new ItemInteraction("first", InteractionGate.class);

        assertTrue(registry.add(first));
        assertThrows(IllegalStateException.class, () -> registry.add(replacement));
        assertSame(first, registry.find("FIRST"));
        assertSame(first, registry.find(InteractionDefault.class));
    }

    private static final class TestItemManager extends ItemManager {
        private void loadDefaults() {
            loadItemInteractions();
        }
    }
}
