package com.srth.plugin.habbo.Jobs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class Job {

  private THashMap<Integer, JobEmpleado> jobEmpleado = new THashMap<Integer, JobEmpleado>();

  private String name_job;
  private int room_id;
  private int sueldo;
  private String look_male;
  private String look_female;
  private int time_turno = 600;
  private int sueldo_turno = 0;

  public Job(String nombre_job) {
    this.name_job = nombre_job;
    this.room_id = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_" + this.getName() + ".id");
  }

  public THashMap<Integer, JobEmpleado> getEmpleados() {
    return this.jobEmpleado;
  }

  public JobEmpleado getEmpleado(Habbo habbo) {
    return getEmpleados().get(habbo.getHabboInfo().getId());
  }

  public void setEmpleado(Habbo habbo) throws SQLException {
    if (getEmpleados().get(habbo.getHabboInfo().getId()) == null) {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
      habboRp.setJobActive(RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(name_job));
      jobEmpleado.put(habbo.getHabboInfo().getId(), new JobEmpleado(habbo, getName()));
    }
  }

  public void removeEmpleado(Habbo habbo) {
    if (getEmpleados().get(habbo.getHabboInfo().getId()) != null) {
      jobEmpleado.remove(habbo.getHabboInfo().getId());
    }
  }

  public void setSueldo(int sueldo) {
    RoleplayWebsocketsRoomsInfo.getConfig().register(getName() + ".sueldo", sueldo + "");
    this.sueldo = sueldo;
  }

  public void setRoom(int room) {
    RoleplayWebsocketsRoomsInfo.getConfig().checkSettings("global_room.job_" + getName() + ".id", room + "");
    RoleplayWebsocketsRoomsInfo.getConfig().updateData("global_room.job_" + getName() + ".id", room + "");
    this.room_id = room;

  }

  public void setLookMale(String look) {

    this.look_male = look;

    if (RoleplayWebsocketsRoomsInfo.getConfig().getValue(getName() + ".look_male") == "none") {
      RoleplayWebsocketsRoomsInfo.getConfig().register(getName() + ".look_male", look);
    } else {
      RoleplayWebsocketsRoomsInfo.getConfig().saveToDatabase(getName() + ".look_male", look);
    }
  }

  public void setLookFemale(String look) {

    this.look_female = look;

    if (RoleplayWebsocketsRoomsInfo.getConfig().getValue(getName() + ".look_female") == "none") {
      RoleplayWebsocketsRoomsInfo.getConfig().register(getName() + ".look_female", look);
    } else {
      RoleplayWebsocketsRoomsInfo.getConfig().saveToDatabase(getName() + ".look_female", look);
    }

  }

  public void resetLook(Habbo habbo) {
    try {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      // Establecer el ultimo look
      habbo.getHabboInfo().setLook(habboRp.getValue("global_ultimo_look_save"));

      if (habbo.getHabboInfo().getCurrentRoom() != null) {
        habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        habbo.getClient().sendResponse(new UserDataComposer(habbo));
      }
    } catch (Exception e) {
    }
  }

  public String getLookFemale() {
    return this.look_female;
  }

  public String getLookMale() {
    return this.look_male;
  }

  public int getSueldo() {
    return this.sueldo;
  }

  public String getName() {
    return this.name_job;
  }

  public int getRoomId() {
    return this.room_id;
  }

  public int getTimeTurnoInSeconds() {
    return time_turno;
  }

  public void setTimeTurnoInSeconds(int time_turno) {
    this.time_turno = time_turno;
  }

  public int getSueldoTurno() {
    return this.sueldo_turno;
  }

  public void setSueldoTurno(int value) {
    this.sueldo_turno = value;
  }

  public LinkedHashMap<Integer, Integer> getMostTurnos() {
    // Usamos LinkedHashMap para mantener el orden de la consulta (descendente)
    // Key: user_id, Value: turnos
    LinkedHashMap<Integer, Integer> mostTurnos = new LinkedHashMap<>();

    try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT user_id, turnos FROM rp_jobs_tasks_users_levels " +
                "WHERE job = ? AND turnos > 0 " +
                "ORDER BY turnos DESC LIMIT 5")) {

      statement.setString(1, this.getName());

      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          mostTurnos.put(set.getInt("user_id"), set.getInt("turnos"));
        }
      }
    } catch (SQLException e) {
      Emulator.getLogging().logSQLException(e);
    }

    // mostTurnos.forEach((userId, cantidad) -> {
    // Habbo habbo =
    // Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
    // String nombre = (habbo != null) ? habbo.getHabboInfo().getUsername() :
    // "Desconectado (ID: " + userId + ")";
    // System.out.println(nombre + " tiene " + cantidad + " turnos.");
    // });

    return mostTurnos;
  }

  public void addTurno(Habbo habbo) {
    int userId = habbo.getHabboInfo().getId();
    String jobName = this.getName();
    int existingId = -1;

    // 1. Intentamos obtener el ID existente
    try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
        PreparedStatement checkStmt = connection.prepareStatement(
            "SELECT id FROM rp_jobs_tasks_users_levels WHERE user_id = ? AND job = ? LIMIT 1")) {

      checkStmt.setInt(1, userId);
      checkStmt.setString(2, jobName);

      try (ResultSet rs = checkStmt.executeQuery()) {
        if (rs.next()) {
          existingId = rs.getInt("id");
        }
      }

      // 2. Lógica de Update o Insert basada en el ID encontrado
      if (existingId != -1) {
        // Caso: EXISTE -> Actualizamos por ID (más rápido y seguro)
        try (PreparedStatement updateStmt = connection.prepareStatement(
            "UPDATE rp_jobs_tasks_users_levels SET turnos = turnos + 1 WHERE id = ?")) {
          updateStmt.setInt(1, existingId);
          updateStmt.executeUpdate();
        }
      } else {
        // Caso: NO EXISTE -> Insertamos nuevo
        try (PreparedStatement insertStmt = connection.prepareStatement(
            "INSERT INTO rp_jobs_tasks_users_levels (user_id, job, turnos) VALUES (?, ?, 1)")) {
          insertStmt.setInt(1, userId);
          insertStmt.setString(2, jobName);
          insertStmt.executeUpdate();
        }
      }

    } catch (SQLException e) {
      Emulator.getLogging().logErrorLine("[JOB SYSTEM] Error al procesar turno para ID: " + userId);
      Emulator.getLogging().logSQLException(e);
    }
  }

}
