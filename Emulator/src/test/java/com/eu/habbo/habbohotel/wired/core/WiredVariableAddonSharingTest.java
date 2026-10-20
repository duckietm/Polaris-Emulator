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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WiredVariableAddonSharingTest {
    private static final int SOURCE_VARIABLE_ID = 500;

    @AfterEach
    void resetSavedSources() {
        WiredUnloadedAddonSources.reset();
    }

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

    @Test
    void anUnloadedSourceRoomSharesFromItsSavedBoxesUntilItChanges() {
        WiredExtraUserVariable variable = sharedVariable();
        WiredExtraVariableTextConnector connector = addon(WiredExtraVariableTextConnector.class, 40, 1.0);
        WiredExtraVariableAddonShare share =
                configured(new WiredExtraVariableAddonShare(43, 1, base(), "", 0, 0), 1, 0, 0);
        AtomicInteger loads = new AtomicInteger();
        AtomicLong now = new AtomicLong();
        WiredUnloadedAddonSources.useForTests(
                (roomId, itemId) -> {
                    loads.incrementAndGet();
                    assertEquals(10, roomId);
                    assertEquals(SOURCE_VARIABLE_ID, itemId);
                    return new WiredUnloadedAddonSources.Saved(77, variable, List.of(variable, connector, share));
                },
                now::get);

        WiredExtraVariableReference reference = reference();
        WiredExtraVariableAddonImport importBox =
                configured(new WiredExtraVariableAddonImport(44, 1, base(), "", 0, 0), 1, 0, 0);
        Room room = room(1, 2, new LinkedHashSet<>(List.of(reference, importBox)), reference);
        when(room.getId()).thenReturn(20);
        when(room.getOwnerId()).thenReturn(77);

        assertEquals(
                List.of(connector),
                WiredVariableAddonSharing.importedAddons(room, reference, WiredVariableAddonSharing.TEXT_CONNECTORS));
        WiredVariableAddonSharing.importedAddons(room, reference, WiredVariableAddonSharing.TEXT_CONNECTORS);
        assertEquals(1, loads.get(), "the saved boxes are read once");

        // Saving, moving or picking up a box there invalidates the room; an entry also expires.
        WiredUnloadedAddonSources.invalidate(10);
        WiredVariableAddonSharing.importedAddons(room, reference, WiredVariableAddonSharing.TEXT_CONNECTORS);
        assertEquals(2, loads.get());
        WiredUnloadedAddonSources.invalidate(11);
        now.set(WiredUnloadedAddonSources.TTL_MS);
        WiredVariableAddonSharing.importedAddons(room, reference, WiredVariableAddonSharing.TEXT_CONNECTORS);
        assertEquals(3, loads.get());

        when(room.getOwnerId()).thenReturn(78);
        assertTrue(
                WiredVariableAddonSharing.importedAddons(room, reference, WiredVariableAddonSharing.TEXT_CONNECTORS)
                        .isEmpty(),
                "another owner's room shares nothing");
    }

    @Test
    void onlyTheBoxTypesSharingReadsAreBuiltFromSavedFurni() {
        assertTrue(WiredUnloadedAddonSources.isRead(WiredExtraUserVariable.class));
        assertTrue(WiredUnloadedAddonSources.isRead(WiredExtraVariableAddonShare.class));
        assertTrue(WiredUnloadedAddonSources.isRead(WiredExtraTimeUtilities.class));
        assertFalse(WiredUnloadedAddonSources.isRead(WiredExtraVariableAddonImport.class));
        assertFalse(WiredUnloadedAddonSources.isRead(WiredExtraVariableReference.class));
        assertFalse(WiredUnloadedAddonSources.isRead(null));
    }

    @Test
    void importedFxMapTheSourceRoomsVariablesToThisRoomsReferences() {
        WiredExtraUserVariable variable = sharedVariable();
        WiredExtraVariableFx fx = addon(WiredExtraVariableFx.class, 60, 1.0);
        when(fx.isUserFx()).thenReturn(true);
        WiredExtraVariableAddonShare share =
                configured(new WiredExtraVariableAddonShare(43, 1, base(), "", 0, 0), 0, 0, 1);
        share.setZ(2.0);
        WiredUnloadedAddonSources.useForTests(
                (roomId, itemId) -> new WiredUnloadedAddonSources.Saved(0, variable, List.of(variable, fx, share)),
                () -> 0L);

        WiredExtraVariableReference reference = reference();
        WiredExtraVariableAddonImport importBox =
                configured(new WiredExtraVariableAddonImport(44, 1, base(), "", 0, 0), 0, 0, 1);
        // More references to the same source room: a second one to the shown variable, and one to its
        // variable 600 (say the fx's range or audience variable).
        WiredExtraVariableReference second = reference();
        when(second.getId()).thenReturn(950);
        when(second.getX()).thenReturn((short) 8);
        WiredExtraVariableReference range = reference();
        when(range.getId()).thenReturn(905);
        when(range.getSourceVariableItemId()).thenReturn(600);
        when(range.getX()).thenReturn((short) 8);
        WiredExtraVariableReference elsewhere = reference();
        when(elsewhere.getId()).thenReturn(906);
        when(elsewhere.getSourceRoomId()).thenReturn(11);
        when(elsewhere.getSourceVariableItemId()).thenReturn(601);
        when(elsewhere.getX()).thenReturn((short) 8);

        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getId()).thenReturn(20);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtras())
                .thenReturn(new LinkedHashSet<>(List.of(reference, importBox, second, range, elsewhere)));
        when(specialTypes.getExtras(1, 2)).thenReturn(new LinkedHashSet<>(List.of(reference, importBox)));

        List<WiredVariableAddonSharing.ImportedFx> imported = WiredVariableAddonSharing.importedFx(room, 25);
        assertEquals(1, imported.size());
        WiredVariableAddonSharing.ImportedFx shown = imported.get(0);
        assertSame(fx, shown.box());
        assertSame(reference, shown.reference());
        assertEquals(905, shown.localId(600));
        assertEquals(900, shown.localId(SOURCE_VARIABLE_ID), "the lowest reference id wins");
        assertEquals(601, shown.localId(601), "another room's variable keeps its id (and so reads as nothing)");
        assertEquals(Map.of(SOURCE_VARIABLE_ID, 900, 600, 905), WiredVariableAddonSharing.referencedIds(room, 10));
    }
}
