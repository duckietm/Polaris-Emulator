package com.srth.plugin.habbo;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowUserTimeProcess;

import java.sql.SQLException;

public class HabboCocinar {
    
    private int user_id;
    private Habbo habbo;
    private HabboClient client;

    private int user_cocinando;
    private int user_ingredientes;
    private String platillo;
    private HabboRp habboRp;

    public HabboCocinar(Habbo habbo) throws SQLException {

        this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        this.habbo = habbo;
        this.client = HabboClientManager.getHabboClient(this.habbo);
        this.user_id = this.habbo.getHabboInfo().getId();

        this.user_cocinando = habboRp.checkSettings("cocina_user.cocinando",0);

        if(habboRp.getNewInventoryHabbo().getItem("ingredientes_variados") != null){
            this.user_ingredientes = habboRp.getNewInventoryHabbo().getItem("ingredientes_variados").getCantidad();
        } else {
            this.user_ingredientes = 0;
        }

        this.platillo = habboRp.checkSettings("cocina_user.name.platillo", "0");
    }

    public void showTimeProcess(String imagen, String descripcion, int tiempo_segundos, Boolean active) throws SQLException{
        
        int enabled = 1;
        String imagen_convert;

        if(active == false){
            enabled = 0;
        }

        if(imagen == ""){
            imagen_convert = imagen;
        } else {
            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            imagen_convert = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications").replace("%url.hotel.base%", url_hotel_base)+imagen;
        }

        this.client.send(new ShowUserTimeProcess("ShowUserTimeProcess", true, enabled, imagen_convert, descripcion, tiempo_segundos));
        
    }

    public void ocultarTimeProcess() throws SQLException{
        this.client.send(new ShowUserTimeProcess("ShowUserTimeProcess", false, 0, "", "", 0));
    }

    public int getUserId() {
        return user_id;
    }

    public int getCocinando() {
        return user_cocinando;
    }
    
    public int setCocinando(int value) {
        habboRp.updateData("cocina_user.cocinando", value+"");
        this.user_cocinando = value;
        return this.user_cocinando;
    }

    public String getPlatillo() {
        return this.platillo;
    }
    
    public String setPlatillo(String value) {
        this.platillo = value;
        habboRp.updateData("cocina_user.name.platillo", value+"");
        return this.platillo;
    }

    public void setIngredientes(int value){
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getNewInventoryHabbo().addCantidadItem("ingredientes_variados", value);
            if(habboRp.getNewInventoryHabbo().getItem("ingredientes_variados") != null){
                int operacion = habboRp.getNewInventoryHabbo().getItem("ingredientes_variados").getCantidad()+value;
                this.user_ingredientes = operacion;
            } else {
                this.user_ingredientes = value;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void restIngredientes(int value){
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            int resultado = habboRp.getNewInventoryHabbo().removeCantidadItem("ingredientes_variados", value);
            this.user_ingredientes = resultado;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getIngredientes() {
        return this.user_ingredientes;
    }
}
