package com.srth.plugin.ActionItems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleplayFurniMenuManagerTest {
    @Test
    void registeredContextMenuCanContainSubmenus() {
        RoleplayFurniMenuManager manager = new RoleplayFurniMenuManager(habbo -> true);
        RoleplayMenu submenu =
                new RoleplayMenu("Cafetería", List.of(new RoleplayMenu.Button("Comprar café", "buy_coffee", true)));
        manager.registerContextMenu(
                "custom_cafe",
                new RoleplayMenu("Acciones", List.of(new RoleplayMenu.Button("Cafetería", "coffee", true, submenu))));

        RoleplayMenu menu = manager.getContextMenu("CUSTOM_CAFE", mock(Habbo.class));

        assertNotNull(menu);
        assertEquals(
                "Comprar café",
                menu.page(42, List.of("coffee")).buttons().get(0).label());
    }

    @Test
    void registeringContextMenuRequiresAValidFurniNameAndMenu() {
        RoleplayFurniMenuManager manager = new RoleplayFurniMenuManager(habbo -> true);

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.registerContextMenu(
                        " ", new RoleplayMenu("Acciones", List.of(new RoleplayMenu.Button("Abrir", "open", true)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.registerContextMenu(
                        null, new RoleplayMenu("Acciones", List.of(new RoleplayMenu.Button("Abrir", "open", true)))));
        assertThrows(NullPointerException.class, () -> manager.registerContextMenu("custom_cafe", null));
    }
}
