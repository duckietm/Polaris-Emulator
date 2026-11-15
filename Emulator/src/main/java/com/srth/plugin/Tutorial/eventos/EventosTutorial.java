package com.srth.plugin.Tutorial.eventos;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;

public class EventosTutorial {

    public EventosTutorial() {

    }

    public void eventBuyIngredientes(Habbo habbo) {
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 4) {

                habboTutorial.incrementParteTutorial(habbo);
                habboTutorial.ocult(habbo);

                Emulator.getThreading().run(() -> {
                    habboTutorial.show(habbo, true);
                }, 5000);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventCreateHomeRoleplay(Habbo habbo) {
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 5) {

                habboRp.getHabboTutorial().incrementParteTutorial(habbo);
                habboRp.getHabboTutorial().show(habbo, true);

            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public void eventOpenPuertaHome(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 6) {
                habboRp.getHabboTutorial().incrementParteTutorial(habbo);
                habboRp.getHabboTutorial().show(habbo, true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventCocinar(int pase, Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (pase == 1) {
                if (habboTutorial.getParteTutorial() == 7) {
                    habboRp.getHabboTutorial().incrementParteTutorial(habbo);
                    habboRp.getHabboTutorial().show(habbo, true);
                }
            } else if (pase == 2) {
                if (habboTutorial.getParteTutorial() == 8) {
                    habboRp.getHabboTutorial().incrementParteTutorial(habbo);
                    Emulator.getThreading().run(() -> {
                        habboRp.getHabboTutorial().show(habbo, true);
                    }, 1000);
                }
            } else if (pase == 3) {
                if (habboTutorial.getParteTutorial() == 9) {
                    habboRp.getHabboTutorial().incrementParteTutorial(habbo);
                    Emulator.getThreading().run(() -> {
                        habboRp.getHabboTutorial().show(habbo, true);
                    }, 1000);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventComerFoodPrimary(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 10) {
                habboRp.getHabboTutorial().incrementParteTutorial(habbo);
                Emulator.getThreading().run(() -> {
                    habboRp.getHabboTutorial().show(habbo, true);
                }, 2000);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventComerFoodSecundary(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 11) {

                habboRp.registerCache("platillo_colocado", "0");

                habboRp.getHabboTutorial().incrementParteTutorial(habbo);
                Emulator.getThreading().run(() -> {

                    habboRp.getHabboTutorial().show(habbo, true);

                }, 2000);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventRecogerPlatilloBasura(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
            if (habboTutorial.getParteTutorial() == 12) {
                habboRp.getHabboTutorial().incrementParteTutorial(habbo);

                Emulator.getThreading().run(() -> {
                    habboRp.getHabboTutorial().show(habbo, true);
                }, 2000);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventDormirCompleted(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 14) {
                habboRp.getHabboTutorial().incrementParteTutorial(habbo);

                Emulator.getThreading().run(() -> {
                    habboRp.getHabboTutorial().show(habbo, true);
                }, 5000);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventDepositarDineroCajero(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 16) {

                habboRp.getHabboTutorial().incrementParteTutorial(habbo);

                Emulator.getThreading().run(() -> {
                    habboRp.getHabboTutorial().show(habbo, true);
                }, 5000);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventPescarPecesLago(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 18) {

                habboTutorial.ocult(habbo);

                habboTutorial.incrementParteTutorial(habbo);

                Emulator.getThreading().run(() -> {
                    habboTutorial.show(habbo, true);
                }, 5000);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eventFinishTutorial(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (habboTutorial.getParteTutorial() == 19) {

                habboTutorial.ocult(habbo);

                habboTutorial.incrementParteTutorial(habbo);

                Emulator.getThreading().run(() -> {
                    habboTutorial.show(habbo, true);
                    habboRp.getTareas().showButtonTareas();
                    habboTutorial.setCompleted(true);
                }, 1000);

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
