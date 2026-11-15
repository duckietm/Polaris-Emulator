package com.srth.plugin.Tutorial.partes_empleos;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;

public class EventosEmpleosTutorial {

    Habbo habbo;
    HabboRp habboRp;

    public EventosEmpleosTutorial(Habbo habbo) {
        this.habbo = habbo;
        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        } catch (Exception e) {
        }
    }

    public void eventActiveEmpleoMedico() {
        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.1.0")) {

            habboTutorial.setParteTutorialExtra("1.1.1");
            habboTutorial.showTutorialEmpleo("1.1.1", true);
            habboRp.getHabboRetos().updateProgressReto(1);

        }
    }

    public void eventTomarJeringa() {

        if (habboRp.getJobActive() == null || habboRp.getJobActive() != null
                && !habboRp.getJobActive().getName().equalsIgnoreCase("medico")) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/job_medico/work_disabled.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.need.active"));
            return;
        }

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }
        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.1.1")) {

            habboTutorial.setParteTutorialExtra("1.1.2");
            habboTutorial.showTutorialEmpleo("1.1.2", true);

            habboRp.getHabboRetos().updateProgressReto(2);

        }
    }

    public void finaliceTutorialMedico() {

        if (habboRp.getJobActive() == null || habboRp.getJobActive() != null
                && !habboRp.getJobActive().getName().equalsIgnoreCase("medico")) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/job_medico/work_disabled.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.need.active"));
            return;
        }

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }
        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.1.2")) {

            habboRp.getHabboRetos().updateProgressReto(3);

            habboTutorial.setTutorialEmpleoActive(false);
            habboTutorial.setParteTutorialExtra("0.0.0");

            habboRp.getHabboRetos().comprobateSuccessRetoActive();

            habboTutorial.ocultTutorialEmpleo(habbo);

        }

    }

    public void eventActiveEmpleoAseo() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }
        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.2.0")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboTutorial.setParteTutorialExtra("1.2.1");
            habboTutorial.showTutorialEmpleo("1.2.1", true);

            habboRp.getHabboRetos().updateProgressReto(1);

        }
    }

    public void eventRecogerBasuraBotesUrban() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.2.1")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboTutorial.setParteTutorialExtra("1.2.2");
            habboTutorial.showTutorialEmpleo("1.2.2", true);

            habboRp.getHabboRetos().updateProgressReto(2);
        }
    }

    public void eventRecogerBasuraBotesUrban20Botes() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboRp.getNewInventoryHabbo().getItem("basura").getCantidad() <= 19) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.2.2")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboTutorial.setParteTutorialExtra("1.2.3");
            habboTutorial.showTutorialEmpleo("1.2.3", true);

            habboRp.getHabboRetos().updateProgressReto(3);
        }
    }

    public void eventTirarBotesDeBasuraContenedor() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboRp.getNewInventoryHabbo().getItem("basura").getCantidad() <= 19) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.2.3")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboTutorial.setParteTutorialExtra("1.2.4");
            habboTutorial.showTutorialEmpleo("1.2.4", true);

            habboRp.getHabboRetos().updateProgressReto(4);
        }
    }

    public void eventLimpiarBoteDeBasuraAseo() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.2.4")) {

            habboRp.getHabboRetos().updateProgressReto(5);

            habboTutorial.setTutorialEmpleoActive(false);
            habboTutorial.setParteTutorialExtra("0.0.0");
            habboTutorial.ocultTutorialEmpleo(habbo);

            habboRp.getHabboRetos().comprobateSuccessRetoActive();
        }
    }

    public void eventActivarEmpleoPolicia() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.3.1")) {

            habboRp.getHabboRetos().updateProgressReto(2);

            habboTutorial.ocultTutorialEmpleo(habbo);
            habboTutorial.setParteTutorialExtra("1.3.2");

            Emulator.getThreading().run(() -> {
                habboTutorial.showTutorialEmpleo(habboTutorial.getParteTutorialExtra(), true);
            }, 2000);

        }

    }

    public void eventMirarExpedienteUsuario() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.3.2")) {
            habboRp.getHabboRetos().updateProgressReto(3);
            habboTutorial.ocultTutorialEmpleo(habbo);
            habboTutorial.setParteTutorialExtra("1.3.3");

            Emulator.getThreading().run(() -> {
                habboTutorial.showTutorialEmpleo(habboTutorial.getParteTutorialExtra(), true);
            }, 1000);
        }
    }

    public void eventArrestarUsuario() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }

        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.3.3")) {

            habboRp.getHabboRetos().updateProgressReto(4);
            habboTutorial.setTutorialEmpleoActive(false);
            habboTutorial.setParteTutorialExtra("0.0.0");
            habboTutorial.ocultTutorialEmpleo(habbo);

        }
    }

    public void eventActiveEmpleoCafetero() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }
        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.4.0")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboTutorial.setParteTutorialExtra("1.4.1");
            habboTutorial.showTutorialEmpleo("1.4.1", true);

            habboRp.getHabboRetos().updateProgressReto(1);

        }
    }

    public void eventTomarGranosDeCafe() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }
        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.4.1")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboTutorial.setParteTutorialExtra("1.4.2");
            habboTutorial.showTutorialEmpleo("1.4.2", true);

            habboRp.getHabboRetos().updateProgressReto(2);
        }
    }

    public void eventPrepararCafeMaquina() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }
        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.4.2")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboTutorial.setParteTutorialExtra("1.4.3");
            habboTutorial.showTutorialEmpleo("1.4.3", true);

            habboRp.getHabboRetos().updateProgressReto(3);
        }
    }

    public void eventColocarCafeEstanteria() {

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getTutorialEmpleoActive() == false) {
            return;
        }
        if (habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.4.3")) {

            habboTutorial.ocultTutorialEmpleo(habbo);

            habboRp.getHabboRetos().updateProgressReto(4);
            habboTutorial.setTutorialEmpleoActive(false);
            habboTutorial.setParteTutorialExtra("0.0.0");
            habboTutorial.ocultTutorialEmpleo(habbo);
        }
    }
}
