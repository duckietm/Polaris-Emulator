package com.srth.plugin.Tutorial.partes_delitos;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;

public class EventosDelitosTutorial {

    public EventosDelitosTutorial() {

    }

    public void eventRecibirArmaIlegal(Habbo habbo) {

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

            inventoryHabboNew.createItem("arma_ilegal", 1);

            if (inventoryHabboNew.getItem("municion") == null || inventoryHabboNew.getItem("municion") != null
                    && inventoryHabboNew.getItem("municion").getCantidad() <= 1) {
                inventoryHabboNew.addCantidadItem("municion", 5);
            }

            habboRp.getHabboTutorial().ocultTutorialDelitos();

            Emulator.getThreading().run(() -> {
                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/arma_active.png?v=1",
                        "Acabas de recibir tu arma ilegal y municiones..."
                                .replace("%br%", "\n"));
            }, 500);

            Emulator.getThreading().run(() -> {
                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/actions/inventory.png?v=1",
                        "Abre tu inventario, equipala y usala..."
                                .replace("%br%", "\n"));
            }, 4000);

            Emulator.getThreading().run(() -> {

                habboRp.getHabboRetos().updateProgressReto(1);
                habboTutorial.setParteTutorialExtra("2.2.1");

                habboTutorial.showTutorialDelitos(habboTutorial.getParteTutorialExtra(), true);
            }, 10000);
        } catch (Exception e) {
        }
    }

    public void eventEquiparArmaIlegal(Habbo habbo) {

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
            InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

            if (!habboRp.getHabboTutorial().getParteTutorialExtra().equalsIgnoreCase("2.2.1")) {
                return;
            }

            habboRp.getHabboTutorial().ocultTutorialDelitos();

            Emulator.getThreading().run(() -> {

                habboRp.getHabboRetos().updateProgressReto(2);
                habboTutorial.setParteTutorialExtra("2.2.2");

                habboTutorial.showTutorialDelitos(habboTutorial.getParteTutorialExtra(), true);
            }, 500);

        } catch (Exception e) {
        }

    }

    public void eventAsesinarPvpMano(Habbo habbo) {

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
            InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

            habboRp.getHabboTutorial().ocultTutorialDelitos();

            if (!habboRp.getHabboTutorial().getParteTutorialExtra().equalsIgnoreCase("2.1.0")
                    && !habboRp.getHabboTutorial().getParteTutorialExtra().equalsIgnoreCase("2.2.2")) {
                return;
            }

            habboRp.getHabboRetos().updateProgressReto(1);
            habboTutorial.setParteTutorialExtra("2.1.1");
            habboTutorial.showTutorialDelitos(habboTutorial.getParteTutorialExtra(), true);

            habboRp.getHabboRetos().removeRetoActive();

        } catch (Exception e) {
        }

    }

    public void eventAsesinarShooterPvp(Habbo habbo) {

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
            InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

            habboRp.getHabboTutorial().ocultTutorialDelitos();

            if (habboRp.getHanditem() != 2002
                    || !habboRp.getHabboTutorial().getParteTutorialExtra().equalsIgnoreCase("2.2.2")) {
                return;
            }

            habboRp.getHabboRetos().updateProgressReto(3);
            habboTutorial.setParteTutorialExtra("2.2.3");

            habboRp.getHabboRetos().removeRetoActive();

            habboTutorial.showTutorialDelitos(habboTutorial.getParteTutorialExtra(), true);

        } catch (Exception e) {
        }

    }

    public void eventRecibirLlaveCajeroAutomatico(Habbo habbo) {

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

            inventoryHabboNew.createItem("llave", 1);

            habboRp.getHabboTutorial().ocultTutorialDelitos();

            Emulator.getThreading().run(() -> {
                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/hack_cajeros/llave.png?v=1",
                        "Acabas de recibir tu arma llave, equipala y usala."
                                .replace("%br%", "\n"));
            }, 500);

            Emulator.getThreading().run(() -> {

                habboRp.getHabboRetos().updateProgressReto(1);
                habboTutorial.setParteTutorialExtra("2.3.1");

                habboTutorial.showTutorialDelitos(habboTutorial.getParteTutorialExtra(), true);
            }, 10000);

        } catch (Exception e) {
        }
    }

    public void eventForzarCajero(Habbo habbo) {

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (!habboTutorial.getParteTutorialExtra().equalsIgnoreCase("2.3.1")) {
                return;
            }

            habboRp.getHabboTutorial().ocultTutorialDelitos();
            Emulator.getThreading().run(() -> {
                habboRp.getHabboRetos().updateProgressReto(2);
                habboTutorial.setParteTutorialExtra("2.3.2");
                habboTutorial.showTutorialDelitos(habboTutorial.getParteTutorialExtra(), true);

                habboRp.getHabboRetos().removeRetoActive();

            }, 500);

        } catch (Exception e) {
        }
    }

}
