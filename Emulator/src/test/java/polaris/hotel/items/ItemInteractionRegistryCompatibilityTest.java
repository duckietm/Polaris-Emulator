package polaris.hotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.items.interactions.InteractionGate;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredContractPayment;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredContractReward;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredContractTrade;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredCustomContract;

class ItemInteractionRegistryCompatibilityTest {

    @Test
    void ourBoxesOnAnOfficialInteractionTypeArePickedByTheFurniName() {
        TestItemManager manager = new TestItemManager();
        manager.loadDefaults();

        assertEquals(
                polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveFurni.class,
                manager.resolveInteraction("wf_act_remove_furni", "wf_act_pickup_furni")
                        .getType());
        assertEquals(
                polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveTemporaryFurni.class,
                manager.resolveInteraction("wf_act_remove_furni", "wf_act_remove_furni")
                        .getType());
        assertEquals(
                polaris.hotel.items.interactions.wired.effects.WiredEffectPlaceFurni.class,
                manager.resolveInteraction("WF_ACT_PLACE_FURNI", "wf_act_spawn_furni")
                        .getType());
        assertEquals(
                polaris.hotel.items.interactions.wired.effects.WiredEffectMoveFurniAsGroup.class,
                manager.resolveInteraction("wf_act_move_furni_as_group", "wf_act_move_group_step")
                        .getType());
        // The name only counts on its official counterpart: another interaction type stays what it is.
        assertEquals(
                manager.getItemInteraction("wf_act_toggle_state").getType(),
                manager.resolveInteraction("wf_act_toggle_state", "wf_act_pickup_furni")
                        .getType());
        // Their own interaction types keep working.
        assertEquals(
                polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveFurni.class,
                manager.resolveInteraction("wf_act_pickup_furni", "anything").getType());
    }

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
