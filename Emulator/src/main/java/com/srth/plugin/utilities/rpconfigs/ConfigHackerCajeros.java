package com.srth.plugin.utilities.rpconfigs;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigHackerCajeros {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigHackerCajeros.class);

    public void load() {
        this.HackerCajeros();
    }

    public void HackerCajeros() {
        try {

            RoleplayWebsocketsRoomsInfo.getConfig().register("seasonal.rol.primary.type.its.credits", "0");
            RoleplayWebsocketsRoomsInfo.getConfig().register("seasonal.rol.primary.type", "2");
            RoleplayWebsocketsRoomsInfo.getConfig().register("seasonal.rol.primary.item.starts.name", "CASH");

            RoleplayWebsocketsRoomsInfo.getConfig().register("global_room.category.roleplay.id", "100");
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_url.look.nitro.imager",
                    "http://www.localhost:3030/?figure=%LOOK%&gesture=sml&amp;&amp;size=0&amp;direction=2&amp;head_direction=2&amp;crr=3&amp;gesture=sml&amp;frame=3");

            ////////////////
            // Hack cajeros
            ////////////////
            RoleplayWebsocketsRoomsInfo.getConfig().register("hotel.furni.rol.forcerot.level_hacker", "1");

            /// Otros ///
            RoleplayWebsocketsRoomsInfo.getConfig().register("hack_cajero.active.time.interval", "120");
            RoleplayWebsocketsRoomsInfo.getConfig().register("hack_cajero.action.success.time.interval", "30");

            // WORK POLICES
            RoleplayWebsocketsRoomsInfo.getConfig().register("job_police_look_male",
                    "m:lg-3023-92.ch-3215-108.sh-3027-110-110.cc-3039-108.ca-1813-62:My look");
            RoleplayWebsocketsRoomsInfo.getConfig().register("job_police_look_female",
                    "f:sh-907-110.ch-824-62.lg-720-92.cc-5810407-108-94:My look");
            RoleplayWebsocketsRoomsInfo.getConfig().register("job_police_sueldo_amount", "3");
            RoleplayWebsocketsRoomsInfo.getConfig().register("robar.time.interval", "180");

            // HOSPITAL
            RoleplayWebsocketsRoomsInfo.getConfig().register("global_room.work_police.id", "1");
            RoleplayWebsocketsRoomsInfo.getConfig().register("prisionero_look_male",
                    "m:ch-215-1323.lg-270-1323.sh-3027-62-62.cc-999999778-62.ca-1000000788-62:prisionero");
            RoleplayWebsocketsRoomsInfo.getConfig().register("prisionero_look_female",
                    "f:ch-640-1323.lg-695-1323.sh-3016-62.ca-1000000788-62:prisionera");

        } catch (Exception e) {
            LOGGER.error("[ROLEPLAY HACKER CAJEROS] Falló al cargar la configuración.", e);
        }
    }
}
