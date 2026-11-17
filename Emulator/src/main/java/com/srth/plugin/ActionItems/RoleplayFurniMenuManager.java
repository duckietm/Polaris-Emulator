package com.srth.plugin.ActionItems;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.HabboRpManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RoleplayFurniMenuManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleplayFurniMenuManager.class);

    private final Map<String, RoleplayMenu> menus;

    public RoleplayFurniMenuManager() {
        this(RoleplayFurniMenuManager::canRobCajero);
        int buttonCount = this.menus.values().stream()
                .mapToInt((menu) -> menu.buttons().size())
                .sum();
        LOGGER.info(
                "Roleplay furni menu manager initialized successfully with {} menus and {} buttons loaded.",
                this.menus.size(),
                buttonCount);
    }

    RoleplayFurniMenuManager(Predicate<Habbo> canRobCajero) {
        this.menus = new ConcurrentHashMap<>(Map.ofEntries(
                Map.entry("rp_urban_basura", menu("Acciones", button("Buscar en basura", "actionBuscarBasuraUrbana"))),
                Map.entry("opti_pizzas", menu("Acciones", button("Comprar comida", "actionComprarComidaMaquina"))),
                Map.entry("opti_pommes", menu("Acciones", button("Comprar comida", "actionComprarComidaMaquina"))),
                Map.entry(
                        "rp_ingred_cocina_1", menu("Acciones", button("Comprar comida", "actionComprarIngredientes"))),
                Map.entry(
                        "rp_ingred_cocina_2", menu("Acciones", button("Comprar comida", "actionComprarIngredientes"))),
                Map.entry("hosptl_cab1", menu("Acciones", button("Buscar jeringa", "actionObtenerJeringa"))),
                Map.entry("cofre_madera_rpbyth_a1", menu("Acciones", button("Abrir cofre", "actionOpenCofreMadera"))),
                Map.entry(
                        "atm_cajero_byth_rp",
                        menu(
                                "Acciones",
                                button("Abrir Cajero", "AbrirCajeroAutomatico"),
                                buttonIf("Robar Cajero", "ForzarCajeroAutomatico", canRobCajero))),
                Map.entry("rp_arbol_talar_byth", menu("Acciones", button("Talar Árbol", "actionTalarArbolMadera"))),
                Map.entry("jp_c22_water", menu("Acciones", button("Pescar", "TirarCanaPescar"))),
                Map.entry("scanner_work_medico", menu("Acciones", button("Conseguir empleo", "ConseguirEmpleoMedico"))),
                Map.entry(
                        "scanner_cafeteros", menu("Acciones", button("Conseguir empleo", "ConseguirEmpleoCafeteros"))),
                Map.entry("scanner_police", menu("Acciones", button("Conseguir empleo", "ConseguirEmpleoPolice"))),
                Map.entry("scanner_work_aseo", menu("Acciones", button("Conseguir empleo", "ConseguirEmpleoAseo"))),
                Map.entry("ktchn_c15_stove_rp", menu("Acciones", button("Cocinar en horno", "ConseguirEmpleoMedico"))),
                Map.entry("val14_door", menu("Acciones", button("Abrir puerta", "AbrirPuertaHome"))),
                Map.entry("prison_gate", menu("Acciones", button("Abrir puerta prision", "AbrirPuertaPrision"))),
                Map.entry("stone_police", menu("Acciones", button("Picar roca", "PicarRocaPrision"))),
                Map.entry(
                        "rp_estanteria_cafe",
                        menu(
                                "Estanteria",
                                button("Comprar café", "actionComprarCafe"),
                                button("Colocar café estanteria", "actionColocarCafeEstanteria"))),
                Map.entry(
                        "habblet_sacodecafe", menu("Estanteria", button("Tomar granos café", "actionTomarGranosCafe"))),
                Map.entry(
                        "eric18_spar_cafe", menu("Estanteria", button("Preparar taza de café", "actionPrepararCafe"))),
                Map.entry("kasja_g_rock7", menu("Rocas", button("Minar Roca", "actionMinarRoca")))));
    }

    public void registerContextMenu(String furniName, RoleplayMenu menu) {
        if (furniName == null || furniName.isBlank())
            throw new IllegalArgumentException("Furni name must not be blank.");

        String key = normalizeName(furniName);
        this.menus.put(key, Objects.requireNonNull(menu));
    }

    public Menu getMenu(String furniName, Habbo habbo) {
        RoleplayMenu menu = this.getContextMenu(furniName, habbo);
        if (menu == null) return null;

        return new Menu(
                menu.title(),
                menu.buttons().stream()
                        .map(button -> new Button(button.label(), button.actionId(), button.visibleIf()))
                        .toList());
    }

    public RoleplayMenu getContextMenu(String furniName, Habbo habbo) {
        if (furniName == null || furniName.isBlank()) {
            return null;
        }

        RoleplayMenu menu = this.menus.get(normalizeName(furniName));

        if (menu == null) {
            return null;
        }

        return menu.visibleTo(habbo);
    }

    private static boolean canRobCajero(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return false;
        }

        HabboRpManager manager = RoleplayWebsocketsRoomsInfo.getHabboRpManager();

        if (manager == null) {
            return false;
        }

        HabboRp habboRp = manager.getHabboRps().get(habbo.getHabboInfo().getId());
        return habboRp != null && habboRp.getJobActive() == null;
    }

    private static String normalizeName(String furniName) {
        return furniName.trim().toLowerCase(Locale.ROOT);
    }

    private static RoleplayMenu menu(String title, RoleplayMenu.Button... buttons) {
        return new RoleplayMenu(title, List.of(buttons));
    }

    private static RoleplayMenu.Button button(String label, String actionId) {
        return buttonIf(label, actionId, (habbo) -> true);
    }

    private static RoleplayMenu.Button buttonIf(String label, String actionId, Predicate<Habbo> visibleIf) {
        return new RoleplayMenu.Button(label, actionId, true, Objects.requireNonNull(visibleIf));
    }

    public record Button(String label, String actionId, Predicate<Habbo> visibleIf) {
        public Button {
            Objects.requireNonNull(label);
            Objects.requireNonNull(actionId);
            Objects.requireNonNull(visibleIf);
        }
    }

    public record Menu(String title, List<Button> buttons) {
        public Menu {
            Objects.requireNonNull(title);
            buttons = List.copyOf(buttons);
        }
    }
}
