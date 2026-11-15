package com.srth.plugin.Turnos;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Turnos.Timers.TimerTurnosJob;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

public class TurnosManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TurnosManager.class);
    private static TurnosManager instance;
    private TimerTurnosJob timerJob;

    private final ConcurrentHashMap<Habbo, HabboTurnos> habboTurnosList = new ConcurrentHashMap<>();

    public TurnosManager() {
        this.init();
    }

    private void init() {
        try {
            if (this.timerJob == null || this.timerJob.isDisposed()) {
                this.timerJob = new TimerTurnosJob();
                Emulator.getThreading().run(this.timerJob);
                LOGGER.info("[ROLEPLAY WEBSOCKETS ROOM INFO] | TimerTurnosJob ha sido iniciado correctamente.");

                this.habboTurnosList.clear();
            }
        } catch (Exception e) {
            LOGGER.error("[ROLEPLAY WEBSOCKETS ROOM INFO] | Error al iniciar el Timer de Turnos: ", e);
        }
    }

    public static TurnosManager getInstance() {
        if (instance == null) {
            instance = new TurnosManager();
        }
        return instance;
    }

    public void stopTimer() {
        if (this.timerJob != null) {
            this.timerJob.setDisposed(true);
        }
    }

    public void showTimeTurnos(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", true, Outcoming.ShowTurnosJobs, this.getDataTurno(habbo)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ocultTimeTurnos(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", false, Outcoming.ShowTurnosJobs, null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showTimeTurnosAction(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", true, Outcoming.ShowTurnosJobs, this.getDataTurno(habbo)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JsonArray getDataTurno(Habbo habbo) throws SQLException {

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base);

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        JsonArray TarrayTask = new JsonArray();
        JsonObject tasks = new JsonObject();

        // Creamos el objeto para los datos del trabajo
        JsonObject jobData = new JsonObject();
        tasks.addProperty("id", habbo.getHabboInfo().getId());

        if (habboRp.getJobActive() != null) {
            JobEmpleado jobEmpleado = habboRp.getJobActive().getEmpleado(habbo);
            if (jobEmpleado != null) {
                tasks.addProperty("hasJob", true);
                jobData.addProperty("jobName", habboRp.getJobActive().getName()); // Nombre del empleo
                jobData.addProperty("remainingTime", jobEmpleado.getJobTimeRemaining());
                jobData.addProperty("imagen", urlNotifications + "RoleplayWebsocketsRoomsInfo/turnos/job_"
                        + habboRp.getJobActive().getName() + ".png");
                jobData.addProperty("descripcion", "¡Tu proxíma paga por cumplir un turno como empleado de "
                        + habboRp.getJobActive().getName() + "!");
            }

            // Añadimos el objeto de trabajo al usuario
            tasks.add("jobInfo", jobData);
        }

        HabboTurnos habboTurnos = this.getHabboTurnos(habbo);
        if (habboTurnos != null) {
            // Añadimos el objeto de trabajo al usuario
            tasks.add("turnInfo", habboTurnos.getTurnosObjet());
        }

        TarrayTask.add(tasks);
        return TarrayTask;
    }

    public HabboTurnos getHabboTurnos(Habbo habbo) {
        return this.habboTurnosList.get(habbo);
    }

    public void registerTurno(Habbo habbo) {
        HabboTurnos habboTurnos = new HabboTurnos(habbo);
        this.habboTurnosList.put(habbo, habboTurnos);
    }
}