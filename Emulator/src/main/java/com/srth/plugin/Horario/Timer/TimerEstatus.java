package com.srth.plugin.Horario.Timer;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Horario.HorarioManager;
import com.srth.plugin.Turnos.HabboTurnos;
import com.srth.plugin.habbo.HabboRp;

public class TimerEstatus extends Scheduler {

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    private HorarioManager horarioManager;

    public TimerEstatus(HorarioManager horarioManager) {
        super(1000); // Intervalo base de 1 segundo
        this.horarioManager = horarioManager;
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);
        }
    }

    @Override
    public void run() {
        if (this.disposed)
            return;

        try {
            int actualSegundos = this.horarioManager.getSegundosRestantes() - 1;

            if (actualSegundos <= 0) {
                // El manager cambia de estado (ej. de dia a tarde) y actualiza el fondo
                this.horarioManager.comprobateStatus();

                // Forzamos la actualización inmediata en las salas al ocurrir el cambio de
                // estado
                actualizarTurnosHorarioEnSalas(true);
            } else {
                this.horarioManager.setSegundosRestantes(actualSegundos);

                // Cada 10 segundos actualizamos el contador restante en la interfaz
                if (actualSegundos % 10 == 0) {
                    actualizarTurnosHorarioEnSalas(false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (!this.disposed) {
                Emulator.getThreading().run(this, 1000);
            }
        }
    }

    /**
     * Recorre las salas de Roleplay y actualiza el turno/tiempo restante a cada
     * usuario
     */
    private void actualizarTurnosHorarioEnSalas(boolean esCambioDeEstado) {
        int rpCategoryId = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");
        int segundosRestantes = this.horarioManager.getSegundosRestantes();
        String turnoActual = this.horarioManager.getTimeHorario();
        String turnoAnterior = this.horarioManager.getEstadoAnterior();

        for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            if (room.getCategory() != rpCategoryId)
                continue;

            for (Habbo habbo : room.getHabbos()) {
                try {
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    if (habboRp != null && habboRp.getHabboClient() != null) {
                        HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
                        if (habboTurnos != null) {

                            // Si hubo un cambio de estado, removemos explícitamente el turno anterior
                            if (esCambioDeEstado && turnoAnterior != null
                                    && !turnoAnterior.equalsIgnoreCase(turnoActual)) {
                                habboTurnos.removeTurno(turnoAnterior);
                            }

                            // Por seguridad limpiamos también cualquier otro residuo por si acaso
                            habboTurnos.removeTurno("dia");
                            habboTurnos.removeTurno("tarde");
                            habboTurnos.removeTurno("noche");

                            // Registramos el turno activo con su respectivo tiempo restante
                            habboTurnos.registerTurno(
                                    turnoActual,
                                    segundosRestantes,
                                    "Tiempo para que se acabe el estado de " + turnoActual,
                                    0,
                                    "/horarios/" + turnoActual + ".png");

                            RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);

                        }
                    }
                } catch (Exception e) {
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

    public HorarioManager getHorarioManager() {
        return this.horarioManager;
    }
}