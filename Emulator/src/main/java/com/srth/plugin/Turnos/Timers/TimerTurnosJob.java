package com.srth.plugin.Turnos.Timers;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Turnos.HabboTurnos;
import com.srth.plugin.Turnos.TurnoObjet;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.JobEmpleado;

public class TimerTurnosJob extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimerTurnosJob.class);
    private int tickCounter = 0; // Contador para controlar el envío cada 10 segundos

    public TimerTurnosJob() {
        // Ahora se ejecuta cada 1 segundo
        super(1);
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            this.run();
        }
    }

    @Override
    public void run() {
        super.run();

        // Si no hay nadie online, reseteamos el counter y salimos
        if (Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().isEmpty()) {
            tickCounter = 0;
            return;
        }

        // Incrementamos el contador cada segundo
        tickCounter++;
        // Variable booleana para saber si toca enviar la actualización visual este
        // segundo
        boolean shouldUpdateVisuals = (tickCounter >= 10);

        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos()
                .entrySet()) {
            Habbo habbo = map.getValue();

            if (habbo == null || !habbo.isOnline())
                continue;

            final Room room = habbo.getHabboInfo().getCurrentRoom();

            if (room != null) {
                int rpCategoryId = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");

                if (room.getCategory() != rpCategoryId) {
                    continue;
                }

                try {
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    Job jobActive = habboRp.getJobActive();

                    // --- Lógica de Trabajo Activo ---
                    if (jobActive != null) {
                        JobEmpleado empleado = jobActive.getEmpleado(habbo);
                        int remainingTime = empleado.getJobTimeRemaining();

                        if (remainingTime > 0) {
                            // Restamos 1 segundo en lugar de 10
                            int newTime = Math.max(0, remainingTime - 1);
                            empleado.setJobTimeRemaining(newTime);

                            // Solo enviamos el paquete si han pasado 10 segundos
                            if (shouldUpdateVisuals) {
                                RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnosAction(habbo);
                            }
                        } else {
                            // Finalización de turno
                            jobActive.addTurno(habbo);
                            empleado.setJobTimeRemaining(jobActive.getTimeTurnoInSeconds());
                            int sueldo = jobActive.getSueldoTurno();
                            habboRp.givePoints(sueldo);

                            habbo.whisper("¡Tu turno ha terminado y se ha incrementado!");

                            // Siempre enviamos al terminar el turno para actualizar el nuevo tiempo
                            RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnosAction(habbo);
                        }
                    }

                    // --- Lógica de HabboTurnos List ---
                    HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
                    if (habboTurnos != null) {
                        for (TurnoObjet jsonElement : habboTurnos.getTurnosList().values()) {
                            int remainingTime = jsonElement.getTime();
                            if (remainingTime > 0) {
                                // Restamos 1 segundo
                                int newTime = Math.max(0, remainingTime - 1);
                                jsonElement.setTime(newTime);

                                // RoleplayWebsocketsRoomsInfo.utilities().LOGGER(jsonElement.time+" its time: ("+jsonElement.name+")");

                                // Enviamos actualización visual si corresponde
                                if (shouldUpdateVisuals) {
                                    RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnosAction(habbo);
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    LOGGER.error("Error en TimerTurnosJob para el usuario: " + habbo.getHabboInfo().getUsername(), e);
                }
            }
        }

        // Si ya pasaron los 10 segundos, reiniciamos el contador
        if (shouldUpdateVisuals) {
            tickCounter = 0;
        }
    }

    // ... (El resto de tus métodos formatSeconds, isDisposed, etc., se mantienen
    // igual)
}