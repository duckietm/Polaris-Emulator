package com.srth.plugin.Retos;

import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

public class HabboRetos {

    private Habbo habbo;
    private HabboRp habboRp;
    private CategoriaReto categoriaRetoActive;
    private TareaCategoria retoActive;

    private int intCategoria;
    private int intReto;

    private static final String KEY_CAT = "tutorial_retos.categoria.active";
    private static final String KEY_RETO = "tutorial_retos.reto.active";
    private static final String FORMAT_PROGRESS = "tutorial_retos_%d_%d";

    public HabboRetos(Habbo habbo) {
        this.habbo = habbo;
        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        } catch (Exception e) {
            logError(e);
        }

        try {
            habboRp.checkSettings(KEY_CAT, "0");
            habboRp.checkSettings(KEY_RETO, "0");

            this.intCategoria = habboRp.getInt(KEY_CAT);
            this.intReto = habboRp.getInt(KEY_RETO);

            if (refreshRetoStatus()) {
                habboRp.getHabboTutorial().setCompleted(true);
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * Actualiza las referencias de objetos basadas en los IDs actuales
     * 
     * @return true si se encontró un reto válido
     */
    private boolean refreshRetoStatus() {
        if (intCategoria == 0)
            return false;

        CategoriaReto cat = RoleplayWebsocketsRoomsInfo.getRetosTutorialManager().getCategoria(intCategoria);
        if (cat != null) {
            this.categoriaRetoActive = cat;
            this.retoActive = cat.getRetos().get(intReto);
            return this.retoActive != null;
        }
        return false;
    }

    public void eventEnterRoom(Habbo habbo) {
        try {
            HabboTutorial tutorial = habboRp.getHabboTutorial();

            if (tutorial.getTutorialEmpleoActive() && retoActive != null) {
                int progress = getParteProgressReto();
                String progressKey = String.format("%d.%d.%d", categoriaRetoActive.getCategoria(), retoActive.getId(),
                        progress);

                if (categoriaRetoActive.getCategoria() == 1) {
                    tutorial.showTutorialEmpleo(progressKey, false);
                } else {
                    tutorial.showTutorialDelitos(progressKey, false);
                }
            }

            comprobateSuccessRetoActive();

            // Sincronizar IDs y refrescar vista si hay un reto activo
            this.intCategoria = habboRp.getInt(KEY_CAT);
            this.intReto = habboRp.getInt(KEY_RETO);

            if (refreshRetoStatus()) {
                showRetoActive(intCategoria, intReto);
            }

        } catch (Exception e) {
            logError(e);
        }
    }

    public void setCategoriaRetoActive(int categoria) {
        this.intCategoria = categoria;
        habboRp.updateData(KEY_CAT, String.valueOf(categoria));
        this.categoriaRetoActive = RoleplayWebsocketsRoomsInfo.getRetosTutorialManager().getCategoria(categoria);
    }

    public void setRetoActive(int reto) {
        this.intReto = reto;
        habboRp.updateData(KEY_RETO, String.valueOf(reto));
        if (categoriaRetoActive != null) {
            this.retoActive = categoriaRetoActive.getRetos().get(reto);
        }
    }

    public void showRetoActive(int typeCategoria, int retoId) {
        setCategoriaRetoActive(typeCategoria);
        setRetoActive(retoId);

        JsonArray data = getArrayRetoActive(typeCategoria, retoId);
        if (data != null) {
            try {
                habboRp.getHabboClient()
                        .send(new MessageManager("MessageManager", true, Outcoming.RetosTutorialActiveRight, data));
            } catch (Exception e) {
                logError(e);
            }
        }
    }

    public void ocultRetoActive() {
        try {
            habboRp.getHabboClient()
                    .send(new MessageManager("MessageManager", false, Outcoming.RetosTutorialActiveRight, null));
        } catch (Exception e) {
            logError(e);
        }
    }

    public JsonArray getArrayRetoActive(int typeCategoria, int retoId) {
        CategoriaReto cat = RoleplayWebsocketsRoomsInfo.getRetosTutorialManager().getCategoria(typeCategoria);
        if (cat == null)
            return null;

        TareaCategoria reto = cat.getRetos().get(retoId);
        if (reto == null)
            return null;

        String formatName = String.format(FORMAT_PROGRESS, cat.getCategoria(), reto.getId());

        JsonObject json = new JsonObject();
        json.addProperty("img", reto.getImgSecundary());
        json.addProperty("title", reto.getTitle());
        json.addProperty("desc", reto.getDesc());
        json.addProperty("event", reto.getEvent());
        json.addProperty("events_required", reto.getEventsRequired());
        json.addProperty("progreso", habboRp.checkSettings(formatName, "0"));
        json.addProperty("pistaText", "¡Quiero ver el tutorial!");

        JsonArray wrapper = new JsonArray();
        wrapper.add(json);
        return wrapper;
    }

    public int getParteProgressReto() {
        if (retoActive == null)
            return 0;
        String formatName = String.format(FORMAT_PROGRESS, categoriaRetoActive.getCategoria(), retoActive.getId());
        return habboRp.getInt(formatName);
    }

    public void updateProgressReto(int progress) {
        if (retoActive == null)
            return;
        String formatName = String.format(FORMAT_PROGRESS, categoriaRetoActive.getCategoria(), retoActive.getId());
        habboRp.updateData(formatName, String.valueOf(progress));
    }

    public void comprobateSuccessRetoActive() {
        if (retoActive == null || categoriaRetoActive == null)
            return;

        if (getParteProgressReto() >= retoActive.getEventsRequired()) {
            String msg = RoleplayWebsocketsRoomsInfo.getTexts()
                    .getValue("retos.tutorial.reto.completed")
                    .replace("%reto_name%", retoActive.getTitle());

            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/reto_completed.png", msg);

            ocultRetoActive();
            removeRetoActive();
            // Actualizar DB para persistir el fin del reto
            habboRp.updateData(KEY_CAT, "0");
            habboRp.updateData(KEY_RETO, "0");
        }
    }

    public void removeRetoActive() {
        this.ocultRetoActive();
        this.intCategoria = 0;
        this.intReto = 0;
        this.categoriaRetoActive = null;
        this.retoActive = null;
    }

    private void logError(Exception e) {
        RoleplayWebsocketsRoomsInfo.utilities().LOGGER("HabboRetos Error: " + e.getMessage());
    }

    // Getters
    public CategoriaReto getCategoriaRetoActive() {
        return categoriaRetoActive;
    }

    public TareaCategoria getRetoActive() {
        return retoActive;
    }
}