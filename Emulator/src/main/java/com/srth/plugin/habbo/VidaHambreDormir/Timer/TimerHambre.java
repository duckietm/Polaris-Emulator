package com.srth.plugin.habbo.VidaHambreDormir.Timer;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.habbo.HabboRp;

public class TimerHambre extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimerHambre.class);

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public TimerHambre() {
        super(RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.rooms.hambre.interval"));
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("rp.rooms.hambre.and.vida.enable")) {
            if (this.disposed) {
                this.disposed = false;
                this.run();
            }
        } else {
            this.disposed = true;
        }
    }

    @Override
    public void run() {
        super.run();

        if (Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().size() == 0) {
            return;
        }
        Habbo habbo;
        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos()
                .entrySet()) {

            habbo = map.getValue();
            final Room room = habbo.getHabboInfo().getCurrentRoom();

            if (habbo != null) {
                if (room != null) {
                    if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                            .getInt("global_room.category.roleplay.id"))
                        continue;

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        if (habboRp.getConstructorSalas() == true) {
                            continue;
                        }

                        if (habboRp.getShooterHabbo().getPvpActive() == 1) {
                            continue;
                        }

                        if (habboRp.getHabboTutorial().getCompleted() == true) {
                            habboRp.getVidaHambreDormir().restarHambre(33);
                        }

                        // Mensaje dormir recordatorio aleatorio
                        if (habboRp.getVidaHambreDormir().getDormir() <= 34) {

                            RecorridoTaxi recorrido = RoleplayWebsocketsRoomsInfo
                                    .getControllerRecorridosTaxi()
                                    .getRecorridoTaxi(habbo);
                            if (recorrido != null) {
                                continue;
                            }

                            if (!habbo.getHabboStats().cache.containsKey("msg_dormir_avise")) {

                                habbo.getHabboStats().cache.put("msg_dormir_avise", 3);
                                habbo.getHabboStats().cache.put("msg_dormir_force_down_vida", 2);

                                forceMessageDormir(habboRp);

                            } else if (habbo.getHabboStats().cache.containsKey("msg_dormir_avise")) {

                                int number = (int) habbo.getHabboStats().cache.get("msg_dormir_avise");
                                int operacion = number - 1;
                                habbo.getHabboStats().cache.put("msg_dormir_avise", operacion);

                                if (operacion <= 0) {
                                    forceMessageDormir(habboRp);
                                    habbo.getHabboStats().cache.put("msg_dormir_avise", 2);
                                }

                                int numberForceDownVida = (int) habbo.getHabboStats().cache
                                        .get("msg_dormir_force_down_vida");
                                int operacionForceVida = numberForceDownVida - 1;
                                habbo.getHabboStats().cache.put("msg_dormir_force_down_vida", operacionForceVida);

                                if (operacionForceVida <= 0) {

                                    if (habboRp.getVidaHambreDormir().getVida() <= 0) {

                                        habboRp.getVidaHambreDormir().forceHambre(30);
                                        habboRp.getVidaHambreDormir().restarVida(30);

                                        habbo.getHabboStats().cache.put("msg_dormir_force_down_vida", 2);

                                        if (room.getId() == RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager()
                                                .getRoom("hospital").getRoomId()) {
                                            return;
                                        }

                                        // Comprobar si no tiene sueño
                                        habboRp.getVidaHambreDormir().comprobateDormir();
                                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                                "/RoleplayWebsocketsRoomsInfo/ws_hospital/vida_vacia.png",
                                                "global_alert.dormir.death.avise", habbo.getClient());

                                        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/ws_hospital/vida_vacia.png",
                                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                        .getValue("global_alert.dormir.death.avise"));
                                    }

                                }

                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }

                }
            }

        }
    }

    public void forceMessageDormir(HabboRp habboRp) {
        try {

            Habbo habbo = habboRp.getHabbo();

            // Comprobar si no tiene sueño
            habboRp.getVidaHambreDormir().comprobateDormir();
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "/RoleplayWebsocketsRoomsInfo/dormir/suenos/dormir2.png",
                    "global_alert.dormir.avise", habbo.getClient());

        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }
}
