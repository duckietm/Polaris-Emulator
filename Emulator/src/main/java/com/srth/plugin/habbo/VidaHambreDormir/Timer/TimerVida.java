package com.srth.plugin.habbo.VidaHambreDormir.Timer;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class TimerVida extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimerVida.class);

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public TimerVida() {
        super(RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.rooms.vida.interval"));
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

        Habbo habbo;

        if (Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().size() == 0) {
            return;
        }
        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos()
                .entrySet()) {

            habbo = map.getValue();
            final Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room != null) {

                if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id"))
                    continue;

                if (habbo != null) {

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        if (habboRp.getHabboTutorial().getCompleted() == true) {

                            if (habboRp.getConstructorSalas() == true) {
                                continue;
                            }

                            if (habboRp.getShooterHabbo().getPvpActive() == 1) {
                                continue;
                            }

                            if (habboRp.getVidaHambreDormir().getHambre() > 20) {
                                habboRp.getVidaHambreDormir().sumarVida(20);
                            } else {
                                habboRp.getVidaHambreDormir().restarVida(10);
                            }
                        }

                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }
            }

        }
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }
}
