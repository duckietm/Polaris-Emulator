package com.srth.plugin.habbo.VidaHambreDormir.Timer;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class TimerHigiene extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimerHigiene.class);

    public TimerHigiene() {
        // Intervalo de ejecución leído desde el config (por defecto cada 30 segundos)
        super(RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.rooms.higiene.interval", 30));
        this.reloadConfig();
    }

    @Override
    public void run() {
        super.run();

        // 1. CARGA DE CONFIGURACIÓN POR CICLO
        int decrementoBase = RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.higiene.decrement.base", 20);
        int decrementoTrabajo = RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.higiene.decrement.work", 25);
        int limiteSucio = RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.higiene.limit.dirty", 20);
        int dañoSalud = RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.higiene.health.damage", 10);

        // Tiempo de gracia tras bañarse (ej. 5 minutos)
        int minutosIgnore = RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.higiene.ignore.minutes", 12);
        int rpCategoryId = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");

        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {

            try {
                // Validaciones básicas de estado
                if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null)
                    continue;

                Room room = habbo.getHabboInfo().getCurrentRoom();

                // Solo aplicar en salas de la categoría Roleplay
                if (room.getCategory() != rpCategoryId)
                    continue;

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                if (habboRp == null)
                    continue;

                // 2. EXCEPCIONES: Constructor, PVP activo o si ya está durmiendo
                if (habboRp.getConstructorSalas() ||
                        habboRp.getShooterHabbo().getPvpActive() == 1 ||
                        habboRp.getVidaHambreDormir().getDormirActive()
                        || habboRp.getVidaHambreDormir().getDuchaActive()) {
                    continue;
                }

                // 3. LÓGICA DE "IGNORE_HIGIENE" (COOLDOWN POST-BAÑO)
                // Se asume que VidaHambreDormir tiene el método getLastBathTime() que devuelve
                // un long (timestamp)
                int ultimoBaño = habboRp.getInt("ignore_higiene");

                if (ultimoBaño > 0) {
                    // El usuario se bañó hace poco, saltamos el decremento en este ciclo
                    int nuevo_ultimoBaño = ultimoBaño - 1;
                    habboRp.updateData("ignore_higiene", nuevo_ultimoBaño + "");
                    continue;
                }

                // 4. PROCESO DE DECREMENTO (Solo si terminó el tutorial)
                if (habboRp.getHabboTutorial().getCompleted()) {

                    // Si tiene trabajo activo, el desgaste es mayor
                    int cantidadARestar = (habboRp.getJobActive() != null) ? decrementoTrabajo : decrementoBase;
                    int higieneActual = habboRp.getVidaHambreDormir().getHigiene();

                    if (higieneActual > 0) {
                        // Restar higiene al objeto de datos
                        habboRp.getVidaHambreDormir().restarHigiene(cantidadARestar);

                        // EFECTO VISUAL: Si entra en rango "sucio" (Efecto 10 - Moscas/Humo)
                        if (habboRp.getVidaHambreDormir().getHigiene() <= limiteSucio) {
                            room.giveEffect(habbo.getRoomUnit(), 10, -1);
                            habboRp.setEnable(10);
                            habbo.whisper("¡Necesitas una ducha, hueles realmente mal!");
                        }
                    } else {

                        if (habboRp.getVidaHambreDormir().getVida() <= 0) {
                            continue;
                        }

                        // PENALIZACIÓN EXTREMA: Higiene en 0
                        // Si la vida es mayor a 70, forzarla a 70 (malestar por suciedad)
                        if (habboRp.getVidaHambreDormir().getVida() >= 70) {
                            habboRp.getVidaHambreDormir().forceHambre(70); // Da hambre por malestar
                        }

                        habboRp.getVidaHambreDormir().restarVida(dañoSalud); // Quita vida (Enfermedad)
                        habbo.whisper("Te sientes enfermo por la falta de higiene...");

                        // Efecto visual más severo (Efecto 11)
                        room.giveEffect(habbo.getRoomUnit(), 11, -1);
                        habboRp.setEnable(11);
                    }

                    // NOTA: Aquí puedes disparar el WebSocketClient si quieres actualizar la UI de
                    // React
                    // habboRp.getWebSocket().send(new UpdateStatsComposer(habboRp));
                }

            } catch (Exception e) {
                LOGGER.error("Error en TimerHigiene para Habbo: "
                        + (habbo != null ? habbo.getHabboInfo().getUsername() : "Unknown"), e);
            }
        }
    }

    public void reloadConfig() {
        // Verifica si el sistema general de necesidades está activo
        boolean enabled = RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("rp.rooms.hambre.and.vida.enable", true);
        if (enabled) {
            if (this.disposed) {
                this.disposed = false;
                // No es necesario llamar a run() manualmente aquí, el scheduler lo hará en su
                // próximo tick
            }
        } else {
            this.disposed = true;
        }
    }
}