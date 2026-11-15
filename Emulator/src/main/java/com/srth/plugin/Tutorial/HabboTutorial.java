package com.srth.plugin.Tutorial;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

public class HabboTutorial {

    private Habbo habbo;
    private int parte;
    private boolean completedTutorial = false;
    private boolean tutorialEmpleoActive = false;
    private String parteExtra = "0.0.0";

    public HabboTutorial(Habbo habbo) {
        this.habbo = habbo;

        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            int tutorial_active = habboRp.checkSettings("tutorialCompleted", 0);
            if (tutorial_active == 0) {
                this.setCompleted(false);
            } else {
                this.setCompleted(true);
            }

            this.parte = habboRp.checkSettings("parteTutorial", 0);
            String parteExtraNew = habboRp.checkSettings("parteTutorialEmpleo", "0.0");
            this.parteExtra = parteExtraNew;

            int tutorialEmpleoActive = habboRp.checkSettings("tutorialEmpleoActive", 0);
            boolean tutorialEmpleoActiveBoolean = tutorialEmpleoActive == 1 ? true : false;
            if (tutorialEmpleoActive == 0) {
                this.setTutorialEmpleoActive(tutorialEmpleoActiveBoolean);
            } else {
                this.setTutorialEmpleoActive(tutorialEmpleoActiveBoolean);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public int getParteTutorial() {
        return this.parte;
    }

    public boolean getCompleted() {
        return this.completedTutorial;
    }

    public boolean getTutorialEmpleoActive() {
        return tutorialEmpleoActive;
    }

    public void setTutorialEmpleoActive(boolean value) {
        int valor = 0;
        if (value == true) {
            valor = 1;
        }
        this.updateHabboData("tutorialEmpleoActive", valor + "");
        this.tutorialEmpleoActive = value;
    }

    public void setParteTutorialExtra(String number) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.updateData("parteTutorialEmpleo", number + "");
            this.parteExtra = number;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getParteTutorialExtra() {
        return this.parteExtra;
    }

    public void setCompleted(boolean value) {
        int valor = 0;
        if (value == true) {
            valor = 1;
        }
        this.updateHabboData("tutorialCompleted", valor + "");
        this.completedTutorial = value;
    }

    public void updateHabboData(String key, String value) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.updateData(key, value);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public void incrementParteTutorial(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            int sumatoria = habboRp.getInt("parteTutorial") + 1;
            habboRp.updateData("parteTutorial", sumatoria + "");
            this.parte = sumatoria;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setParteTutorial(int number, Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.updateData("parteTutorial", number + "");
            this.parte = number;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void ocult(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            final HabboClient client = habboRp.getHabboClient();
            // Ocultar resta temporizador
            client.send(new MessageManager("MessageManager", false, Outcoming.TutorialViewDesplegable,
                    null));
        } catch (Exception e) {
        }
    }

    public void show(Habbo habbo, Boolean forceVisible) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            final HabboClient client = habboRp.getHabboClient();
            // Ocultar resta temporizador
            client.send(new MessageManager("MessageManager", true, Outcoming.TutorialViewDesplegable,
                    RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getArray(habbo, forceVisible)));
        } catch (Exception e) {
        }
    }

    public void showPersonalizado(Habbo habbo, int type) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            final HabboClient client = habboRp.getHabboClient();
            // Ocultar resta temporizador
            client.send(new MessageManager("MessageManager", true, Outcoming.TutorialViewDesplegable,
                    RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getArrayPersonalizado(habbo, type)));
        } catch (Exception e) {
        }
    }

    public void ocultTutorialEmpleo(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            final HabboClient client = habboRp.getHabboClient();
            // Ocultar resta temporizador
            client.send(new MessageManager("MessageManager", false, Outcoming.TutorialViewDesplegableEmpleos,
                    null));
        } catch (Exception e) {
        }
    }

    public void showTutorialEmpleo(String type, Boolean forceVisible) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            habboRp.getHabboRetos().showRetoActive(habboRp.getHabboRetos().getCategoriaRetoActive().getCategoria(),
                    habboRp.getHabboRetos().getRetoActive().getId());

            final HabboClient client = habboRp.getHabboClient();
            // Ocultar resta temporizador
            client.send(new MessageManager("MessageManager", true, Outcoming.TutorialViewDesplegableEmpleos,
                    RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getArrayTutorialEmpleo(habbo, type,
                            forceVisible)));
        } catch (Exception e) {
        }
    }

    public void showTutorialDelitos(String type, Boolean forceVisible) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            habboRp.getHabboRetos().showRetoActive(habboRp.getHabboRetos().getCategoriaRetoActive().getCategoria(),
                    habboRp.getHabboRetos().getRetoActive().getId());

            final HabboClient client = habboRp.getHabboClient();
            // Ocultar resta temporizador
            client.send(new MessageManager("MessageManager", true, Outcoming.TutorialViewDesplegableEmpleos,
                    RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getArrayTutorialDelitos(habbo, type,
                            forceVisible)));
        } catch (Exception e) {
        }
    }

    public void ocultTutorialDelitos() {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            habboRp.getHabboRetos().showRetoActive(habboRp.getHabboRetos().getCategoriaRetoActive().getCategoria(),
                    habboRp.getHabboRetos().getRetoActive().getId());

            final HabboClient client = habboRp.getHabboClient();
            // Ocultar resta temporizador
            client.send(new MessageManager("MessageManager", false, Outcoming.TutorialViewDesplegableEmpleos,
                    null));
        } catch (Exception e) {
        }
    }
}
