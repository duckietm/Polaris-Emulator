package com.srth.plugin.habbo.Delitos;

import java.sql.SQLException;
import java.util.Map;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.OutGeneralShowTask;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class HabboEncarceledManager {

    private Habbo habbo;
    private HabboClient client;
    private HabboRp habboRp;

    private int arresto_pendiente;

    private Boolean active;
    private String name;
    private int data_task_progress;
    private int data_task_progress_required;

    String nameTask = "EncarceledMinarRocas";
    int progressTask = 0;
    int progressTaskRequired = 0;

    public Boolean getTaskActive(){
        return this.active;
    }
    public Boolean setTaskActive(Boolean value){
        return this.active = value;
    }

    public String getTaskName(){
        return this.name;
    }

    public String setTaskName(String value){
        return this.name = value;
    }


    public int getTaskActiveProgress(){
        return this.data_task_progress;
    }

    public int setTaskActiveProgress(int value){
        this.data_task_progress = value; 
        return value;
    }

    public int getTaskActiveProgressRequired(){
        return this.data_task_progress_required;
    }

    public HabboEncarceledManager(Habbo habbo){

        try {
            this.habbo = habbo;
            this.client = HabboClientManager.getHabboClient(habbo);
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            String[] task_active;
            String[] task_active_data;

            if(habboRp != null){

                arresto_pendiente = habboRp.getDelitosHabbo().getDelitos();

                // Comprobacion de tarea activa
                task_active = habboRp.checkSettings("general.task.active", "[active:0;task:none;]").replace(";",":").split(":");

                // Registro de sus 2 datos
                    if(task_active[1].equalsIgnoreCase("0")){
                        active = false;
                    } else if(task_active[1].equalsIgnoreCase("1")){
                        active = true;
                    }
                name = task_active[3];
                
                ////////////////////////////////////////////////////
                // Comprobación de datos tarea activa
                task_active_data = habboRp.checkSettings("general.task."+nameTask+".data", "progress:0; progress_required:0;").replace(";",":").split(":");
                // Registro de sus 3 datos
                data_task_progress = Integer.parseInt(task_active_data[1]);
                data_task_progress_required = Integer.parseInt(task_active_data[3]);
                //////////////////////////////////////////////////////
            }
        } catch (Exception e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e+"");
        }
    }


    public void comprobarDelitosTask() throws SQLException{

        String task_data_active = "active:%active%;";
        String task_data_name = "task:%task%;";
        String task_active_data_progress = "progress:%progress%;";
        String task_active_data_progress_required = "progress_required:%progress_required%;";

        if(getTaskActive() == false){
            if(getTaskName().equalsIgnoreCase("none")){

                progressTaskRequired = arresto_pendiente;

                habboRp.update("general.task.active", task_data_active.replace("%active%", "1")+task_data_name.replace("%task%", nameTask));

                habboRp.update("general.task."+nameTask+".data", task_active_data_progress.replace("%progress%", progressTask+"")+task_active_data_progress_required.replace("%progress_required%", progressTaskRequired+""));

                this.setTaskActive(true);
                this.setTaskName(nameTask);
            }

            return;

        } else if(getTaskActive() == true){
            if(!getTaskName().equalsIgnoreCase(nameTask)){

                habboRp.update("general.task.active", task_data_active.replace("%active%", "1")+task_data_name.replace("%task%", nameTask));

                this.setTaskActive(true);
                this.setTaskName(nameTask);
            }
            return;
        }
    }

    public void InsertTaskProgress() throws SQLException{

        String task_data_active = "active:%active%;";
        String task_data_name = "task:%task%;";
        String task_active_data_progress = "progress:%progress%;";
        String task_active_data_progress_required = "progress_required:%progress_required%;";

        if(getTaskName().equalsIgnoreCase(nameTask)){

            int suma_progress = getTaskActiveProgress()+1;

            if(getTaskActiveProgressRequired() <= suma_progress){

                habboRp.update("general.task."+nameTask+".data", task_active_data_progress.replace("%progress%", suma_progress+"")+task_active_data_progress_required.replace("%progress_required%", getTaskActiveProgressRequired()+""));

                habboRp.update("general.task.active", task_data_active.replace("%active%", "0")+task_data_name.replace("%task%", "none"));

                habboRp.updateData("count.arresto.pendiente", "0");

                HabboClient wsClient = HabboClientManager.getHabboClient(habbo);
                wsClient.send(new PlaySound("PlaySound", true, "new_level.mp3", habbo));

                this.setTaskActive(false);
                this.setTaskName("none");

                this.ocultTaskEncarceled();

                // Insertar progreso tarea
                habboRp.getTareas().insertProgress("SerLiberadoPrision");

            } else {

                habboRp.update("general.task."+nameTask+".data", task_active_data_progress.replace("%progress%", suma_progress+"")+task_active_data_progress_required.replace("%progress_required%", getTaskActiveProgressRequired()+""));

                this.setTaskActiveProgress(suma_progress);
                this.showTaskEncarceled();
            }
        }
    }

    public void EventUserEnterRoom(Habbo habbo, Room room, Boolean hospital_active) throws SQLException{

        // int habbo_id = habbo.getHabboInfo().getId();
        
    }

    public void showTaskEncarceled() throws SQLException{
        if(getTaskActive() == true){
            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications").replace("%url.hotel.base%", url_hotel_base)+"RoleplayWebsocketsRoomsInfo//roca.png";
            // String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")+"&headonly=1&size=a&gesture=sml&direction=2&head_direction=2";

            JsonArray Button = new JsonArray();
            JsonObject tasks;

            tasks = new JsonObject();
                tasks.addProperty("active", "1");
                tasks.addProperty("id", 1);
                tasks.addProperty("process", getTaskActiveProgress()+"");
                tasks.addProperty("max_process", getTaskActiveProgressRequired()+"");

                tasks.addProperty("title", "Pica %cantidad% de rocas".replace("%cantidad%", getTaskActiveProgressRequired()+""));
                tasks.addProperty("description", "Debes picar dicha cantidad de rocas para cumplir tu labor social y salir de prisión.");
                tasks.addProperty("image", url_images);
                tasks.addProperty("xp", 1);
                Button.add(tasks);

            client.send(new OutGeneralShowTask("ShowVentanaTaskActive", true, Button));
        }

    }

    public void cancelEncarceledAndTask() throws SQLException{
        
        if(getTaskName().equalsIgnoreCase(nameTask)){
            habboRp.updateData("general.task.active", "[{\"active\":0,\"task\":\"none\"}]");
            habboRp.updateData("general.task."+nameTask+".data", "[]");

            this.ocultTaskEncarceled();
        }
        
    }

    public void ocultTaskEncarceled() throws SQLException{
        client.send(new OutGeneralShowTask("ShowVentanaTaskActive", false, null));
        
    }

    public void registerArrestoPendiente(){
        // Remover notificaciones, delitos.
        habboRp.updateData("count_delitos", "0");

        // Ultimo arresto pendiente
        int count_arrestos = habboRp.checkSettings("count.arresto.pendiente",0);
        int sumatoria = count_arrestos+1;
        habboRp.updateData("count.arresto.pendiente", sumatoria+"");

        // Registrar canceled
        habboRp.checkSettings("its.encarceled", "0");
    }

    public void registerReportePendiente(String accion){
        int ultimo_reporte_pendiente = habboRp.checkSettings("job_polices.reporte.pendiente",0);
        int operacion = ultimo_reporte_pendiente+1;
        if(accion == "remove"){ 
            operacion = ultimo_reporte_pendiente-1;
        }
        habboRp.update("job_polices.reporte.pendiente", operacion+"");
    }

    public void showExpediente(GameClient client, Habbo habbo){
        try {

            HabboRp habboRp = null;

            if(habbo != client.getHabbo()){
                // Cuando estas viendo a otro usuario
                client.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_expediente.success.other").replace("%username%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);
                habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            } else if(habbo == client.getHabbo()) {
                // Cuando te estas viendo a ti mismo
                client.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_expediente.success.auto"), RoomChatMessageBubbles.NORMAL);
                habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());
            }

            THashMap<String, String> codes = new THashMap<>();
            codes.put("USERNAME", habbo.getHabboInfo().getUsername());
            String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
                + "&headonly=0&size=a&gesture=sml&head_direction=3";
            codes.put("URL_LOOK", look.replace("%LOOK%", habbo.getHabboInfo().getLook())); 
            codes.put("ROOMID", habboRp.getHabbo().getHabboInfo().getCurrentRoom().getId() + "");
            codes.put("NUMBER_ARRESTOS", habboRp.getDelitosHabbo().getArrestos()+"");
            codes.put("NUMBER_DELITOS", habboRp.getDelitosHabbo().getDelitos()+"");
            ServerMessage msg = new BubbleAlertComposer("user.expediente", codes).compose();
            client.sendResponse(msg);

        } catch (Exception e) {
        }
    }

    public void registerDelito(Habbo habbo, String descripcion_delito){

        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            int ultimo = habboRp.getInt("count_delitos");
            int sumar = ultimo+1;
            // Guardar delit
            habboRp.updateData("count_delitos", sumar+"");

            this.comprobateDelito(habbo, descripcion_delito);
            
        } catch (Exception e) {
            
        }
    }

    public void unlockItemInventaryRP(String item_name){
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            // Insertar ITEM en inventarioRP
            habboRp.getNewInventoryHabbo().createItem(item_name, 1);
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR(e+"");
        }
    }


    public void comprobateDelito(Habbo habbo, String descripcion_delito){
        
        int count_delitos = habboRp.checkSettings("count_delitos",0);
        if(count_delitos > 2){
            
            this.reportDelitoAllPolices(habbo, descripcion_delito);

            this.registerArrestoPendiente();

            Emulator.getThreading().run(() -> {
                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/job_polices/police_level_1.gif", "interactions_global.delito.reporting.polices", habbo.getClient());
                HabboClient habboClient = HabboClientManager.getHabboClient(habbo);
                try {
                    habboClient.send(new PlaySound("PlaySound", true, "radio_police.mp3", habbo));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }, 1500);
        }
    }

    public void reportDelitoAllPolices(Habbo sender, String descripcion_delito){
        try {
            for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
                Habbo habbos = set.getValue();
    
                HabboRp habbosRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbos);
    
                if(habbosRp.getJobActive().getName().equalsIgnoreCase("police") == false){
                    continue;
                }
    
                RoleplayWebsocketsRoomsInfo.utilities().BubbleAlert(habbos, sender, descripcion_delito, true);
    
            }
        } catch (Exception e) {
        }
    }

}
