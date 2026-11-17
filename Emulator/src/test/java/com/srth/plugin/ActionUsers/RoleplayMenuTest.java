package com.srth.plugin.ActionUsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleplayMenuTest {
    @Test
    void nestedMenuUsesExistingActionButtonToOpenAndReturn() {
        RoleplayMenu menu = new RoleplayMenu(
                "Ciudadano",
                List.of(
                        new RoleplayMenu.Button("Susurrar", "whisper", true),
                        new RoleplayMenu.Button(
                                "Policía",
                                "police",
                                true,
                                new RoleplayMenu(
                                        "Policía", List.of(new RoleplayMenu.Button("Arrestar", "arrest", true))))));

        RoleplayMenu rootPage = menu.page(42, List.of());

        assertEquals("whisper", rootPage.buttons().get(0).actionId());
        assertEquals("Policía", rootPage.buttons().get(1).label());
        RoleplayMenu.Navigation open = RoleplayMenu.parseNavigation(
                        rootPage.buttons().get(1).actionId())
                .orElseThrow();
        assertEquals(42, open.requestId());
        assertEquals(List.of("police"), open.path());

        RoleplayMenu policePage = menu.page(open.requestId(), open.path());

        assertEquals("Policía", policePage.title());
        assertEquals("arrest", policePage.buttons().get(0).actionId());
        assertEquals("Volver", policePage.buttons().get(1).label());
        RoleplayMenu.Navigation back = RoleplayMenu.parseNavigation(
                        policePage.buttons().get(1).actionId())
                .orElseThrow();
        assertEquals(42, back.requestId());
        assertTrue(back.path().isEmpty());
    }

    @Test
    void navigationRejectsUnknownOrDisabledSubmenus() {
        RoleplayMenu menu = new RoleplayMenu(
                "Acciones",
                List.of(new RoleplayMenu.Button(
                        "Policía",
                        "police",
                        false,
                        new RoleplayMenu("Policía", List.of(new RoleplayMenu.Button("Arrestar", "arrest", true))))));

        assertNull(menu.page(42, List.of("police")));
        assertNull(menu.page(42, List.of("missing")));
        assertFalse(RoleplayMenu.parseNavigation("arrest").isPresent());
        assertNotNull(menu.page(42, List.of()));
    }

    @Test
    void actionsCanBeGroupedIntoMultipleSubmenus() {
        RoleplayMenu menu = new RoleplayMenu(
                        "Acciones",
                        List.of(
                                new RoleplayMenu.Button("Susurrar", "whisper", true),
                                new RoleplayMenu.Button("Arrestar", "police_arrest", true),
                                new RoleplayMenu.Button("Curar", "medic_heal", true)))
                .withSubmenuForActions(
                        "Policía", "police", button -> button.actionId().startsWith("police_"))
                .withSubmenuForActions(
                        "Médico", "medic", button -> button.actionId().startsWith("medic_"));

        assertEquals(
                List.of("Susurrar", "Policía", "Médico"),
                menu.buttons().stream().map(RoleplayMenu.Button::label).toList());
        assertEquals(
                "Arrestar", menu.page(42, List.of("police")).buttons().get(0).label());
        assertEquals("Curar", menu.page(42, List.of("medic")).buttons().get(0).label());
    }

    @Test
    void furniMenuVisibilityAppliesToNestedActions() {
        RoleplayMenu menu = new RoleplayMenu(
                "Cafetería",
                List.of(new RoleplayMenu.Button(
                        "Acciones",
                        "coffee",
                        true,
                        habbo -> habbo != null,
                        new RoleplayMenu(
                                "Cafetería", List.of(new RoleplayMenu.Button("Comprar café", "buy_coffee", true))))));

        assertTrue(menu.visibleTo(null).buttons().isEmpty());
        assertNull(menu.visibleTo(null).page(42, List.of("coffee")));
        assertEquals(1, menu.visibleTo(mock(Habbo.class)).buttons().size());
    }
}
