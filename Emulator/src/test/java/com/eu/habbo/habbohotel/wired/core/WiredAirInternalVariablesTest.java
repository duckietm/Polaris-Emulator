package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.BuildersClubPalette;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AIR's packed position variables, event variables and smart variables (turbo P1-12 / P2-21). */
class WiredAirInternalVariablesTest {
    private final Room room = mock(Room.class);

    @Test
    void furniPositionAndOccupationArePacked() {
        HabboItem item = mock(HabboItem.class);
        when(item.getX()).thenReturn((short) 14);
        when(item.getY()).thenReturn((short) 22);
        when(item.getRotation()).thenReturn(4);

        assertEquals(14 << 8 | 22, WiredInternalVariableSupport.readFurniValue(this.room, item, "@position"));
        // The low byte is the rotation's index among the furni's directions (0, 2, 4, 6 here): facing 4 is 2 (AIR).
        assertEquals(
                14 << 16 | 22 << 8 | 2, WiredInternalVariableSupport.readFurniValue(this.room, item, "@occupation"));
        assertEquals(2, WiredInternalVariableSupport.readFurniValue(this.room, item, "@rotation"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination("@position"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination("@occupation"));
    }

    @Test
    void userPositionIsPacked() {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getX()).thenReturn((short) 3);
        when(unit.getY()).thenReturn((short) 9);

        assertEquals(3 << 8 | 9, WiredInternalVariableSupport.readUserValue(this.room, unit, "@position"));
        assertTrue(WiredInternalVariableSupport.hasUserValue(this.room, unit, "@position"));
        assertTrue(WiredInternalVariableSupport.canUseUserDestination("@position"));
        assertFalse(WiredInternalVariableSupport.canUseUserDestination("@occupation"), "furni only");
        assertEquals(3, WiredInternalVariableSupport.positionX(3 << 8 | 9));
        assertEquals(9, WiredInternalVariableSupport.positionY(3 << 8 | 9));
    }

    @Test
    void theTransactionFailedReasonIsReadOnItsOwnEvent() {
        WiredEvent failed = WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, this.room)
                .transactionFailureReason(21)
                .build();
        WiredContext ctx = new WiredContext(failed, null, mock(WiredServices.class), new WiredState(10));

        assertTrue(WiredInternalVariableSupport.canUseContextReference("@event.transaction_failed.reason"));
        assertEquals(21, WiredInternalVariableSupport.readContextValue(ctx, "@event.transaction_failed.reason"));

        WiredEvent other = WiredEvent.builder(WiredEvent.Type.CUSTOM, this.room).build();
        assertNull(WiredInternalVariableSupport.readContextValue(
                new WiredContext(other, null, mock(WiredServices.class), new WiredState(10)),
                "@event.transaction_failed.reason"));
    }

    @Test
    void theLinkSourceRoomIsReadAndZeroOtherwise() {
        WiredEvent received = WiredEvent.builder(WiredEvent.Type.LINK_MESSAGE_RECEIVED, this.room)
                .linkSourceRoomId(812)
                .build();
        WiredEvent other = WiredEvent.builder(WiredEvent.Type.CUSTOM, this.room).build();

        assertEquals(
                812,
                WiredInternalVariableSupport.readContextValue(
                        new WiredContext(received, null, mock(WiredServices.class), new WiredState(10)),
                        "@event.link.source_room_id"));
        assertEquals(
                0,
                WiredInternalVariableSupport.readContextValue(
                        new WiredContext(other, null, mock(WiredServices.class), new WiredState(10)),
                        "@event.link.source_room_id"));
    }

    @Test
    void theRecolorablePartsTakeAirsNamesAndKeepOurs() {
        assertEquals(
                "~recolorable_furni.color.rgb.red",
                WiredInternalVariableSupport.normalizeKey("~recolorable_furni.color.rgb.r"));
        assertEquals(
                "~recolorable_furni.color.rgb.blue",
                WiredInternalVariableSupport.normalizeKey("~recolorable_furni.color.rgb.b"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination("~recolorable_furni.color.rgb.g"));
        assertTrue(BuildersClubPalette.isKey("~recolorable_furni.color.rgb.green"));
        assertTrue(BuildersClubPalette.isKey("~recolorable_furni.color.rgb.g"));
    }

    @Test
    void chestSmartVariablesExistOnlyOnChests() {
        InteractionWiredChest chest = mock(InteractionWiredChest.class);
        ChestStorage contents = mock(ChestStorage.class);
        when(chest.getContents()).thenReturn(contents);
        when(chest.availableAmount()).thenReturn(40);
        when(contents.getCapacity()).thenReturn(500);
        when(contents.isLocked()).thenReturn(true);
        when(contents.isAccessDonate()).thenReturn(true);

        assertEquals(40, WiredSmartVariables.readFurniValue(chest, "~chest.available_amount"));
        assertEquals(500, WiredSmartVariables.readFurniValue(chest, "~chest.capacity"));
        assertEquals(1, WiredSmartVariables.readFurniValue(chest, "~chest.locked"));
        assertEquals(0, WiredSmartVariables.readFurniValue(chest, "~chest.is_auto_lock"));
        assertEquals(1, WiredSmartVariables.readFurniValue(chest, "~chest.is_donatable"));
        assertNull(WiredSmartVariables.readFurniValue(mock(HabboItem.class), "~chest.capacity"));
        assertTrue(WiredInternalVariableSupport.canUseFurniReference("~chest.capacity"));
        assertFalse(WiredInternalVariableSupport.canUseFurniDestination("~chest.capacity"), "read only");
    }

    @Test
    void petHorseAndPlantSmartVariablesFollowTheirKind() {
        Pet dog = mock(Pet.class);
        when(dog.getLevel()).thenReturn(3);
        when(dog.getRespect()).thenReturn(7);
        when(dog.getUserId()).thenReturn(55);
        assertEquals(3, WiredSmartVariables.readUserValue(dog, "~pet.level"));
        assertEquals(7, WiredSmartVariables.readUserValue(dog, "~pet.scratches"));
        assertEquals(55, WiredSmartVariables.readUserValue(dog, "~pet.owner_id"));
        assertEquals(20, WiredSmartVariables.readUserValue(dog, "~pet.max_level"));
        assertNull(WiredSmartVariables.readUserValue(dog, "~horse.has_saddle"), "not a horse");

        HorsePet horse = mock(HorsePet.class);
        Habbo rider = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(9);
        when(rider.getHabboInfo()).thenReturn(info);
        when(horse.hasSaddle()).thenReturn(true);
        when(horse.getRider()).thenReturn(rider);
        assertEquals(1, WiredSmartVariables.readUserValue(horse, "~horse.has_saddle"));
        assertEquals(1, WiredSmartVariables.readUserValue(horse, "~horse.is_riding"));
        assertEquals(9, WiredSmartVariables.readUserValue(horse, "~horse.controller_user_id"));

        MonsterplantPet plant = mock(MonsterplantPet.class);
        when(plant.getRarity()).thenReturn(4);
        when(plant.isDead()).thenReturn(true);
        assertEquals(4, WiredSmartVariables.readUserValue(plant, "~plant.rarity_level"));
        assertEquals(1, WiredSmartVariables.readUserValue(plant, "~plant.is_dead"));
        assertNull(WiredSmartVariables.readUserValue(plant, "~pet.level"), "a plant is not a pet here");
        assertNull(WiredSmartVariables.readUserValue(null, "~pet.level"));
    }

    @Test
    void aLevelUpMovesItsSubVariablesForTheVariableChangedTrigger() {
        int definitionId = 4_322;
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(definition.getId()).thenReturn(definitionId);
        when(definition.hasValue()).thenReturn(true);
        WiredExtraVariableLevelUpSystem levelUp =
                new WiredExtraVariableLevelUpSystem(78, 1, mock(Item.class), "", 0, 0);
        levelUp.saveData(new WiredSettings(new int[] {0xFF, 1, 100, 3}, "", new int[0], 0), null);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getExtras(0, 0))
                .thenReturn(new LinkedHashSet<InteractionWiredExtra>(List.of(levelUp, definition)));
        when(this.room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(this.room.getHabboItem(definitionId)).thenReturn(definition);

        List<WiredVariableLevelSystemSupport.DerivedChange> changes = WiredVariableLevelSystemSupport.derivedChanges(
                this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionId, false, false, 90, 110);
        int currentLevelId = 700_000_000 + definitionId * 16 + WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL + 1;

        WiredVariableLevelSystemSupport.DerivedChange level = changes.stream()
                .filter(change -> change.syntheticItemId() == currentLevelId)
                .findFirst()
                .orElseThrow();
        assertEquals(1, level.previousValue());
        assertEquals(2, level.currentValue());
        assertTrue(
                changes.stream()
                        .noneMatch(change -> change.syntheticItemId()
                                == 700_000_000 + definitionId * 16 + WiredExtraVariableLevelUpSystem.SUB_MAX_LEVEL + 1),
                "a part that keeps its value does not fire");
        assertTrue(WiredVariableLevelSystemSupport.derivedChanges(
                        this.room, WiredVariableLevelSystemSupport.TARGET_USER, definitionId, false, false, 10, 20)
                .stream()
                .noneMatch(change -> change.syntheticItemId() == currentLevelId));
    }
}
