package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonImport;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonShare;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonSharing;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFx;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.messages.ServerMessage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredVariableAddonSharingTest {
    private static final int SOURCE_VARIABLE_ID = 500;

    private static Item base() {
        Item item = mock(Item.class);
        when(item.getSpriteId()).thenReturn(1);
        return item;
    }

    private static <T extends WiredExtraVariableAddonSharing> T configured(T box, int... categories) {
        box.saveData(new WiredSettings(categories, "", new int[0], 0), null);
        return box;
    }

    private static Room room(int x, int y, Set<InteractionWiredExtra> tile, InteractionWiredExtra... byId) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtras(x, y)).thenReturn(tile);
        when(specialTypes.getExtras()).thenReturn(tile);
        for (InteractionWiredExtra extra : byId) {
            when(specialTypes.getExtra(extra.getId())).thenReturn(extra);
        }
        return room;
    }

    private static WiredExtraUserVariable sharedVariable() {
        WiredExtraUserVariable variable = mock(WiredExtraUserVariable.class);
        when(variable.getId()).thenReturn(SOURCE_VARIABLE_ID);
        when(variable.getX()).thenReturn((short) 4);
        when(variable.getY()).thenReturn((short) 5);
        when(variable.isSharedAvailability()).thenReturn(true);
        return variable;
    }

    private static WiredExtraVariableReference reference() {
        WiredExtraVariableReference reference = mock(WiredExtraVariableReference.class);
        when(reference.getId()).thenReturn(900);
        when(reference.getX()).thenReturn((short) 1);
        when(reference.getY()).thenReturn((short) 2);
        when(reference.getSourceRoomId()).thenReturn(10);
        when(reference.getSourceVariableItemId()).thenReturn(SOURCE_VARIABLE_ID);
        when(reference.isUserReference()).thenReturn(true);
        return reference;
    }

    private static <T extends InteractionWiredExtra> T addon(Class<T> type, int id, double z) {
        T addon = mock(type);
        when(addon.getId()).thenReturn(id);
        when(addon.getZ()).thenReturn(z);
        return addon;
    }

    @Test
    void theBoxesStoreTheThreeOfficialCategoryFlags() {
        WiredExtraVariableAddonShare share =
                configured(new WiredExtraVariableAddonShare(1, 1, base(), "", 0, 0), 1, 0, 1);

        assertTrue(share.allows(WiredVariableAddonSharing.TEXT_CONNECTORS));
        assertFalse(share.allows(WiredVariableAddonSharing.SUBVARIABLE_CREATORS));
        assertTrue(share.allows(WiredVariableAddonSharing.VARIABLE_FX));
        assertFalse(share.allows(3));
        assertTrue(share.getWiredData().contains("\"variableFx\":true"));

        share.onPickUp();
        assertFalse(share.allows(WiredVariableAddonSharing.TEXT_CONNECTORS));
    }

    @Test
    void theEditorPacketCarriesTheOfficialCodeAndThreeIntParams() {
        WiredExtraVariableAddonImport box =
                configured(new WiredExtraVariableAddonImport(7, 1, base(), "", 0, 0), 0, 1, 0);
        ServerMessage message = mock(ServerMessage.class);

        box.serializeWiredData(message, null);

        org.mockito.ArgumentCaptor<Integer> ints = org.mockito.ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(message, org.mockito.Mockito.atLeastOnce()).appendInt(ints.capture());
        // sprite, id, then [count, text connectors, subvariable creators, fx], 0 and the code.
        assertEquals(List.of(0, 0, 1, 7, 3, 0, 1, 0, 0, WiredExtraVariableAddonImport.CODE, 0, 0), ints.getAllValues());
    }

    @Test
    void aSharedCategoryHandsOverTheSourcesAddonsInStackOrder() {
        WiredExtraUserVariable variable = sharedVariable();
        WiredExtraVariableTextConnector upper = addon(WiredExtraVariableTextConnector.class, 41, 2.0);
        WiredExtraVariableTextConnector lower = addon(WiredExtraVariableTextConnector.class, 40, 1.0);
        WiredExtraTimeUtilities timeUtilities = addon(WiredExtraTimeUtilities.class, 42, 1.5);
        WiredExtraVariableAddonShare share =
                configured(new WiredExtraVariableAddonShare(43, 1, base(), "", 0, 0), 1, 0, 0);
        share.setX((short) 4);
        share.setY((short) 5);

        Set<InteractionWiredExtra> tile = new LinkedHashSet<>(List.of(variable, upper, timeUtilities, lower, share));
        Room source = room(4, 5, tile, variable);

        assertEquals(
                List.of(lower, upper),
                WiredVariableAddonSharing.sharedAddons(source, reference(), WiredVariableAddonSharing.TEXT_CONNECTORS));
        assertTrue(
                WiredVariableAddonSharing.sharedAddons(
                                source, reference(), WiredVariableAddonSharing.SUBVARIABLE_CREATORS)
                        .isEmpty(),
                "the source does not share subvariable creators");
    }

    @Test
    void nothingIsSharedWithoutAShareBoxOrFromAPrivateVariable() {
        WiredExtraUserVariable variable = sharedVariable();
        WiredExtraVariableTextConnector connector = addon(WiredExtraVariableTextConnector.class, 40, 1.0);
        Room source = room(4, 5, new LinkedHashSet<>(List.of(variable, connector)), variable);

        assertTrue(
                WiredVariableAddonSharing.sharedAddons(source, reference(), WiredVariableAddonSharing.TEXT_CONNECTORS)
                        .isEmpty());

        when(variable.isSharedAvailability()).thenReturn(false);
        assertNull(WiredVariableAddonSharing.sourceDefinition(source, reference()));
        assertNull(WiredVariableAddonSharing.sourceDefinition(null, reference()));
    }

    @Test
    void theImportBoxOnTheReferencesTileDecidesWhatTheRoomTakesOver() {
        WiredExtraVariableReference reference = reference();
        WiredExtraVariableAddonImport importBox =
                configured(new WiredExtraVariableAddonImport(44, 1, base(), "", 0, 0), 0, 0, 1);
        Room room = room(1, 2, new LinkedHashSet<>(List.of(reference, importBox)), reference);

        assertTrue(WiredVariableAddonSharing.imports(room, reference, WiredVariableAddonSharing.VARIABLE_FX));
        assertFalse(WiredVariableAddonSharing.imports(room, reference, WiredVariableAddonSharing.TEXT_CONNECTORS));
        assertFalse(WiredVariableAddonSharing.shares(room, reference, WiredVariableAddonSharing.VARIABLE_FX));
        assertTrue(WiredVariableAddonSharing.importsFx(room));
        // Without a loaded source room there is nothing to take over.
        assertTrue(WiredVariableAddonSharing.importedAddons(room, reference, WiredVariableAddonSharing.VARIABLE_FX)
                .isEmpty());
        assertTrue(WiredVariableAddonSharing.importedFx(room, 25).isEmpty());
        assertNull(WiredVariableAddonSharing.importedAddon(
                room, reference, WiredVariableAddonSharing.VARIABLE_FX, WiredExtraVariableFx.class));
    }

    @Test
    void aPlainVariableImportsNothing() {
        WiredExtraUserVariable variable = sharedVariable();
        Room room = room(4, 5, new LinkedHashSet<>(List.of(variable)), variable);

        assertTrue(WiredVariableAddonSharing.importedAddons(room, variable, WiredVariableAddonSharing.TEXT_CONNECTORS)
                .isEmpty());
        assertSame(null, WiredVariableLevelSystemSupport.getTimeUtilities(room, variable));
    }
}
