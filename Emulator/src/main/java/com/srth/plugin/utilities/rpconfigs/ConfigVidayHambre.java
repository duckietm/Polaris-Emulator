package com.srth.plugin.utilities.rpconfigs;

import com.eu.habbo.Emulator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigVidayHambre {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigVidayHambre.class);

    public void load() {
        this.VidayHambre();
    }

    public void VidayHambre() {
        try {

            RoleplayWebsocketsRoomsInfo.getConfig().register("global.url.hotel.base", "http://localhost");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global_url_images", "%url.hotel.base%/roleplayWebsocketsRoomsBySrThrone/");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global_url.hotel.notifications", "%url.hotel.base%/swf/c_images/notifications/");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global_url.hotel.furnis", "%url.hotel.base%/swf/dcr/hof_furni/");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_url.hotel.photos", "%url.hotel.base%/swf/camera/");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global.look.ruta", "https://imager.nabbo.es/?figure=%LOOK%");

            // Nuevos tiempos del dia
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_rooms_fondo_timeHorario.dia.interval", "600");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_rooms_fondo_timeHorario.tarde.interval", "120");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_rooms_fondo_timeHorario.noche.interval", "300");

            RoleplayWebsocketsRoomsInfo.getConfig().register("global_room.category.roleplay.id", "100");
            Emulator.getConfig().register("global_room.category.roleplay.id", "100");

            Emulator.getConfig().register("hotel.rooms.handitem.time", 2000 + "");
            Emulator.getConfig().update("hotel.rooms.handitem.time", 2000 + "");
            Emulator.getConfig().saveToDatabase();

            RoleplayWebsocketsRoomsInfo.getConfig().register("seasonal.rol.primary.type.its.credits", "0");
            RoleplayWebsocketsRoomsInfo.getConfig().register("seasonal.rol.primary.type", "2");
            RoleplayWebsocketsRoomsInfo.getConfig().register("seasonal.rol.primary.item.starts.name", "CASH");

            RoleplayWebsocketsRoomsInfo.getConfig().register("global_timer_give.cash.interval", "620");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_timer_give.cash.amount", "1");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_timer_give.cash.active", "1");

            // Foods machines y comida
            RoleplayWebsocketsRoomsInfo.getConfig().register("foods_machine.time.espera", "5");
            RoleplayWebsocketsRoomsInfo.getConfig().register("foods_machine.time.active.interval", "10000");
            RoleplayWebsocketsRoomsInfo.getConfig().register("cocina.cantidad.platillos", "6");
            RoleplayWebsocketsRoomsInfo.getConfig().register("foods_machine.price.buy", "1");
            RoleplayWebsocketsRoomsInfo.getConfig().register("comer_succes.ignore.hambre.cantidad", "20");
            RoleplayWebsocketsRoomsInfo.getConfig().register("cofres.shooter.time.active.interval", "50000");

            // Timers
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.hambre.and.vida.enable", "1");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.dormir.enable", "1");

            // segundos
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.hambre.interval", "20");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.vida.interval", "3");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.dormir.interval", "800");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.dormir.decrement.amount", "5");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.energia.walk.tiles.per.point", "10");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.energia.action.cost", "2");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.dormir_time_espera.interval", "60");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.higiene.interval", "60");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.banera_time_espera.interval", "30");

            // Camilla hospital
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.rooms.camillahospital_time_espera.interval", "60");

            // Cargamos los valores de configuración una vez por ciclo para ahorrar recursos
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.higiene.decrement.base", "20");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.higiene.decrement.work", "35");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.higiene.limit.dirty", "20");
            RoleplayWebsocketsRoomsInfo.getConfig().register("rp.higiene.health.damage", "5");

            // Super mercado
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_amount.ingredients.inicie", "9");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_amount.price.ingredients", "3");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_amount.ingredients.buy", "3");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_amount.price.buy.foods.machine", "1");

            // Botes de basura urbano
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_rp_urban_basura.amount", "3;5");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_rp_urban_basura.time.interval", "120000");

            // Tasks times
            RoleplayWebsocketsRoomsInfo.getConfig().register("tasks_times_interval_dais.amount", "1");
            RoleplayWebsocketsRoomsInfo.getConfig().register("tasks_times_interval_current", "1");

            // New rooms roleplay users
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_room.create.name", "[CASA] %username%");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global_room.create.descripcion", "Casa roleplay de %username%");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_room.create.model", "model_a");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_room.create.maxusers", "10");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_room.create.tradetype", "1");

            RoleplayWebsocketsRoomsInfo.getConfig().register("global_rooms_fondo_timeHorario.interval", "1800");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_rooms_fondo_timeHorario", "dia");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global_rooms_fondo_timeHorario_color_dia", "1:140:142:174");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global_rooms_fondo_timeHorario_color_tarde", "1:29:109:128");
            RoleplayWebsocketsRoomsInfo.getConfig()
                    .register("global_rooms_fondo_timeHorario_color_noche", "1:176:191:20");
            RoleplayWebsocketsRoomsInfo.getConfig().register("name_room_prod", "calles_peligrosas_3");
            RoleplayWebsocketsRoomsInfo.getConfig().register("name_room_prod_active", "true");

            RoleplayWebsocketsRoomsInfo.getConfig().register("enable_taxi_id", "750");
            RoleplayWebsocketsRoomsInfo.getConfig().register("enable_taxi_bot_mode", "1");

        } catch (Exception e) {
            LOGGER.error("[WEBSOCKETS ROOMS INFO] Falló al cargar la configuración.", e);
        }
    }
}
