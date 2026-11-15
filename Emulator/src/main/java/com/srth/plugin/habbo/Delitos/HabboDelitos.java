package com.srth.plugin.habbo.Delitos;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Delitos.Task.PicarRocas;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.OutGeneralShowTask;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class HabboDelitos {

    HabboRp habboRp;
    int cajerosRobados;
    int robosCometidos;
    int delitos;
    int arrestos;
    Boolean its_encarleced;
    Habbo habbo;

    String task_data;
    private static THashMap<String, PicarRocas> taskPicarRocas = new THashMap<String, PicarRocas>();

    public HabboDelitos(Habbo habbo) {
        try {

            this.habbo = habbo;
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            this.cajerosRobados = habboRp.checkSettings("cajeros_robados", 0);
            this.robosCometidos = habboRp.checkSettings("robos_cometidos", 0);

            this.delitos = habboRp.checkSettings("delitos", 0);
            this.arrestos = habboRp.checkSettings("arrestos", 0);

            if (habboRp.checkSettings("its.encarceled", 0) == 0) {
                this.its_encarleced = false;
            } else {
                this.its_encarleced = true;
            }

            this.task_data = habboRp.checkSettings("general.task.active", "[{\"active\":0,\"task\":\"none\"}]");

        } catch (Exception e) {

        }
    }

    public void createTaskActive(String name_task) {

        String formato = "[{\"active\":%ACTIVE%,\"task\":\"%NAME_TASK%\"}]".replace("%ACTIVE%", "1")
                .replace("%NAME_TASK%", name_task);
        this.habboRp.updateData("general.task.active", formato);
        this.task_data = formato;

        JsonParser parser = new JsonParser();
        // Obtain Array
        JsonArray gsonArr = parser.parse(this.task_data).getAsJsonArray();
        // for each element of array
        for (JsonElement obj : gsonArr) {
            // Object of array
            JsonObject gsonObj = obj.getAsJsonObject();

            // Primitives elements of object
            String task = gsonObj.get("task").getAsString();
            // Object Constructor
            if (taskPicarRocas.get(task) == null) {
                taskPicarRocas.put(task, new PicarRocas(this.habboRp.getHabbo()));
                this.showTask();
            }
        }
    }

    public void changeLookPrisionero(Habbo habbo) {
        try {

            String look_prisionero = RoleplayWebsocketsRoomsInfo.getConfig().getValue("prisionero_look_male");
            String look_prisionera = RoleplayWebsocketsRoomsInfo.getConfig().getValue("prisionero_look_female");

            String[] data = null;
            if (habbo.getHabboInfo().getGender().name().equalsIgnoreCase("m")) {
                data = look_prisionero.split(":");
            } else if (habbo.getHabboInfo().getGender().name().equalsIgnoreCase("f")) {
                data = look_prisionera.split(":");
            }
            if (data.length < 2) {
                return;
            }

            String gender = data[0];
            String figure = data[1];

            if (gender.isEmpty() || figure.isEmpty() || (!gender.equalsIgnoreCase("m") && !gender.equalsIgnoreCase("f"))
                    || !habbo.getHabboInfo().getGender().name().equalsIgnoreCase(gender)) {
                return;
            }

            String newFigure = "";
            for (String playerFigurePart : habbo.getHabboInfo().getLook().split("\\.")) {
                if (!playerFigurePart.startsWith("ch") && !playerFigurePart.startsWith("lg"))
                    newFigure += playerFigurePart + ".";
            }

            String newFigureParts = figure;
            for (String newFigurePart : newFigureParts.split("\\.")) {
                if (newFigurePart.startsWith("hd"))
                    newFigureParts = newFigureParts.replace(newFigurePart, "");
            }

            String newLook = newFigure + newFigureParts;
            if (newLook.length() > 512)
                return;

            habbo.getHabboInfo()
                    .setLook(newLook);

            habboRp.getHabboClient().getHabbo().getHabboInfo().getCurrentRoom()
                    .sendComposer(new RoomUserDataComposer(habbo).compose());
            habbo.getClient().sendResponse(new UserDataComposer(habbo));

        } catch (Exception e) {
        }

    }

    public void completeTask() {
        String formato = "[{\"active\":%ACTIVE%,\"task\":\"%NAME_TASK%\"}]".replace("%ACTIVE%", "0")
                .replace("%NAME_TASK%", "null");
        this.habboRp.updateData("general.task.active", formato);
        this.task_data = formato;

        taskPicarRocas.remove("EncarceledMinarRocas");
        this.ocultTask();
    }

    public PicarRocas getPicarRocas() {
        return taskPicarRocas.get("EncarceledMinarRocas");
    }

    public void showTask() {
        try {
            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                    .replace("%url.hotel.base%", url_hotel_base)
                    + "RoleplayWebsocketsRoomsInfo/";
            // String look =
            // RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")+"&headonly=1&size=a&gesture=sml&direction=2&head_direction=2";

            JsonArray Button = new JsonArray();
            JsonObject tasks;

            tasks = new JsonObject();
            tasks.addProperty("active", "1");
            tasks.addProperty("id", 1);
            tasks.addProperty("process", this.getPicarRocas().getProgress() + "");
            tasks.addProperty("max_process", this.getPicarRocas().getProgressRequired() + "");

            tasks.addProperty("title", this.getPicarRocas().getTitle());
            tasks.addProperty("description", this.getPicarRocas().getDescripcion());
            tasks.addProperty("image", url_images + this.getPicarRocas().getImg());
            tasks.addProperty("xp", 1);
            Button.add(tasks);

            habboRp.getHabboClient().send(new OutGeneralShowTask("ShowVentanaTaskActive", true, Button));
        } catch (Exception e) {
        }
    }

    public void ocultTask() {
        try {
            habboRp.getHabboClient().send(new OutGeneralShowTask("ShowVentanaTaskActive", false, null));
        } catch (Exception e) {
        }
    }

    public void sendAlertToPolices(Habbo habbo) {
        try {

            Emulator.getThreading().run(() -> {
                try {

                    RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                            "RoleplayWebsocketsRoomsInfo/job_polices/police_level_1.gif",
                            "interactions_global.delito.reporting.polices", habbo.getClient());
                    HabboClient habboClient = HabboClientManager.getHabboClient(habbo);
                    habboClient.send(new PlaySound("PlaySound", true, "radio_police.mp3", habbo));

                } catch (Exception e) {
                }
            }, 1500);

            for (JobEmpleado JobEmpleado : RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("police").getEmpleados()
                    .values()) {
                if (JobEmpleado.getHabbo().getHabboInfo().getCurrentRoom().getId() != RoleplayWebsocketsRoomsInfo
                        .getConfig().getInt("global_room.category.roleplay.id")) {

                    RoleplayWebsocketsRoomsInfo.utilities().BubbleAlert(JobEmpleado.getHabbo(), habbo,
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("forzar.cajero.delito"), true);

                }
            }
        } catch (Exception e) {
        }
    }

    public void incrementRobosCometidos() {
        int sumatoria = this.getRobosCometidos() + 1;
        this.setRobosCometidos(sumatoria);
    }

    public void incrementCajerosRobados() {
        int sumatoria = this.getCajerosRobados() + 1;
        this.setCajerosRobados(sumatoria);
    }

    public boolean getEncarceled() {
        return this.its_encarleced;
    }

    public void setEncarceled(Boolean value) {
        int integer = 0;
        if (value == true) {
            integer = 1;
        }
        this.habboRp.updateData("its.encarceled", integer + "");
        this.its_encarleced = value;
    }

    public int getRobosCometidos() {
        return this.robosCometidos;
    }

    public void setRobosCometidos(int value) {
        this.robosCometidos = value;
        this.habboRp.updateData("robos_cometidos", value + "");
    }

    public int getCajerosRobados() {
        return cajerosRobados;
    }

    public void setCajerosRobados(int value) {
        this.cajerosRobados = value;
        this.habboRp.updateData("cajeros_robados", value + "");
    }

    public int getDelitos() {
        return this.delitos;
    }

    public int getArrestos() {
        return this.arrestos;
    }

    public void incrementArrestos(int value) {
        int sumatoria = this.getArrestos() + value;
        this.arrestos = sumatoria;
        this.habboRp.updateData("arrestos", sumatoria + "");
    }

    public void incrementDelitos(int value) {
        int sumatoria = this.delitos + value;
        this.delitos = sumatoria;
        this.habboRp.updateData("delitos", sumatoria + "");
    }

    public void resetDelitos() {
        this.delitos = 0;
        this.habboRp.updateData("delitos", 0 + "");
    }

    public void exitPrision() {
        try {

            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room == null) {
                return;
            }

            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            // Establecer el ultimo look
            Emulator.getThreading().run(() -> {

                String ultimoLookSave = habboRp.getValue("global_ultimo_look_save");
                habbo.getHabboInfo().setLook(ultimoLookSave);

                if (habbo.getHabboInfo().getCurrentRoom() != null) {
                    habboRp.getHabboClient().getHabbo().getHabboInfo().getCurrentRoom()
                            .sendComposer(new RoomUserDataComposer(habbo).compose());
                    habbo.getClient().sendResponse(new UserDataComposer(habbo));
                }

                if (habbo.getHabboInfo().getLook()
                        .contains("ch-215-1323.lg-270-1323.sh-3027-62-62.cc-999999778-62.ca-1000000788-62")
                        || habbo.getHabboInfo().getLook()
                                .contains("ch-640-1323.lg-695-1323.sh-3016-62.ca-1000000788-62")) {

                    String look_actual = habbo.getHabboInfo().getLook()
                            .replace("ch-215-1323.lg-270-1323.sh-3027-62-62.cc-999999778-62.ca-1000000788-62", "");
                    look_actual = look_actual.replace("ch-640-1323.lg-695-1323.sh-3016-62.ca-1000000788-62", "");
                    habbo.getHabboInfo().setLook(look_actual);

                }

            }, 100);

            RoomLayout roomLayout = room.getLayout();
            String dataRoomTileSave = "x:" + roomLayout.getDoorX() + ";y:" + roomLayout.getDoorY() + ";z:"
                    + roomLayout.getDoorZ();
            habboRp.checkSettings("dataRoomTileSave", dataRoomTileSave);
            habboRp.updateData("dataRoomTileSave", dataRoomTileSave);

            habboRp.updateData("enter_prision_encarceled", "1");

        } catch (Exception e) {
        }
    }

}
