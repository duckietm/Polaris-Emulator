package com.srth.plugin.habbo.Delitos.Task;

import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class PicarRocas {

    public String name_task = "EncarceledMinarRocas";
    public String img = "job_polices/roca.png";
    public String title = "Pica %cantidad% de rocas".replace("%cantidad%", this.getProgressRequired()+"");
    public String descripcion = "Debes picar dicha cantidad de rocas para cumplir tu labor social y salir de prisión.";

    public String data_db;
    public Boolean active;
    public HabboRp habboRp;

    public int progress;
    public int progress_required;
   
    public PicarRocas(Habbo habbo){
        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            
            String data = "[{\"progress\":0,\"progress_required\":%PROGRESS_MAX%}]".replace("%PROGRESS_MAX%", this.habboRp.checkSettings("delitos", 0)+"");
            habboRp.updateData("general.task."+name_task+".data", data);
            this.data_db = habboRp.checkSettings("general.task."+name_task+".data", data);

            JsonParser parser = new JsonParser();
            JsonArray gsonArr = parser.parse(this.data_db).getAsJsonArray();
            for (JsonElement obj : gsonArr) {
                // Object of array
                JsonObject gsonObj = obj.getAsJsonObject();

                // Primitives elements of object
                int progress = gsonObj.get("progress").getAsInt();
                int progress_required = gsonObj.get("progress_required").getAsInt();

                // Object Constructor
                this.progress = progress;
                this.progress_required = progress_required;
                this.title = "Pica %cantidad% de rocas".replace("%cantidad%", this.getProgressRequired()+"");
            }

        } catch (Exception e) {
           
        }

    }

    public void incrementProgress(){
        int sumatoria = this.getProgress()+1;
        String data = "[{\"progress\":%MYPROGRESS%,\"progress_required\":%PROGRESS_MAX%}]".replace("%PROGRESS_MAX%", this.getProgressRequired()+"").replace("%MYPROGRESS%", sumatoria+"");
        habboRp.updateData("general.task."+name_task+".data", data);
        this.data_db = data;
        this.progress = sumatoria;
    }

    public void complete(){
        this.progress = 0;
        this.progress_required= 0;
        String data = "[{\"progress\":%MYPROGRESS%,\"progress_required\":%PROGRESS_MAX%}]".replace("%PROGRESS_MAX%", "0").replace("%MYPROGRESS%", "0");
        this.habboRp.updateData("general.task."+name_task+".data", data);
        this.data_db = data;
    }

    public int getProgress(){
        return this.progress;
    }

    public int getProgressRequired(){
        return this.progress_required;
    }

    public String getTitle(){
        return this.title;
    }

    public String getDescripcion(){
        return this.descripcion;
    }

    public String getImg(){
        return this.img;
    }
}
