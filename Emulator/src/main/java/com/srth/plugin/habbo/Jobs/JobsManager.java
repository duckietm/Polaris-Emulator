package com.srth.plugin.habbo.Jobs;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.Tasks.Task;
import com.srth.plugin.habbo.Jobs.Tasks.TaskManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;

import gnu.trove.map.hash.THashMap;

public class JobsManager {

  private THashMap<String, Job> jobs = new THashMap<String, Job>();

  public JobsManager() {
  }

  public void registerJob(Job job) {
    jobs.put(job.getName(), job);
  }

  public void update() throws SQLException {
    jobs.clear();

    Job medico = new Job("medico");
    medico.setSueldo(2);
    medico.setSueldoTurno(6);
    medico.setLookMale("m:cp-987463014-62.ca-1819-62.sh-290-77.lg-285-92.ch-220-62.cc-568282-77:My look");
    medico.setLookFemale("f:ch-645-62.lg-720-62.sh-725-77.ca-1819-62.cc-568282-77:My look");
    this.registerJob(medico);

    Job aseo = new Job("aseo");
    aseo.setSueldo(2);
    aseo.setTimeTurnoInSeconds(600);
    aseo.setSueldoTurno(6);
    aseo.setLookMale("m:ch-210-1187.lg-270-1187.wa-2009-62.cc-568282-1195.ca-201422-1193-62.sh-3027-62-62:My look");
    aseo.setLookFemale("f:ch-645-1187.lg-695-1187.sh-3027-62-62.cc-568282-110.ca-201422-110-62.wa-2009-62:My look");
    this.registerJob(aseo);

    Job police = new Job("police");
    police.setSueldo(2);
    police.setTimeTurnoInSeconds(600);
    police.setSueldoTurno(6);
    police.setLookMale("m:lg-3023-92.ch-3215-108.sh-3027-110-110.cc-3039-108.ca-1813-62:My look");
    police.setLookFemale("f:sh-907-110.ch-824-62.lg-720-92.cc-5810407-108-94:My look");
    this.registerJob(police);

    Job cafetero = new Job("cafetero");
    cafetero.setSueldo(1);
    aseo.setSueldoTurno(6);
    cafetero.setTimeTurnoInSeconds(600);
    cafetero.setSueldoTurno(6);
    cafetero.setLookMale("m:cc-3289-1408.sh-3068-1408-1408.ch-240-64.lg-285-64:My look");
    cafetero.setLookFemale("f:cc-3289-1408.sh-3068-1408-1408.ch-240-64.lg-285-64:My look");
    this.registerJob(cafetero);

  }

  public Job getJob(String name_job) throws SQLException {
    return getJobs().get(name_job);
  }

  public THashMap<String, Job> getJobs() throws SQLException {
    return jobs;
  }

  public int getSize() throws SQLException {
    return getJobs().values().size();
  }

  public void showMenuJob(Habbo habbo) {
    try {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      if (habboRp.getJobActive() == null) {
        return;
      }

      if (habboRp.getHabboClient() != null) {
        LevelPlayer player = WorkSystemLevelManager.getOnlinePlayers().get(habbo.getHabboInfo().getId());

        // Actualizar las tareas
        new TaskManager().getTasksForPlayer(player);

        habboRp.getHabboClient()
            .send(new MessageManagerDouble("MessageManager", true, Outcoming.OpenMenuJobs, this.getArray(habbo),
                this.getArrayTasks(player)));
      }
    } catch (Exception e) {
    }
  }

  public JsonArray getArrayTasks(LevelPlayer player) throws SQLException {

    JsonArray TarrayTask = new JsonArray();
    JsonObject tasks;

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
    String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
        .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";

    for (Task task : player.getTasks().values()) {

      tasks = new JsonObject();
      tasks.addProperty("id", task.getId());

      if (player.getPlayerInfo().getTaskActive().contains(task.getId())) {

        player.setPlayerStats(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), task.getId());

        tasks.addProperty("active", "1");
        tasks.addProperty("process", player.getPlayerStats().getProcess());
        tasks.addProperty("max_process", task.getProcessRequired());

      } else {
        tasks.addProperty("active", "0");
        tasks.addProperty("process", 0);
        tasks.addProperty("max_process", 0);
      }

      tasks.addProperty("title", task.getName());
      tasks.addProperty("description", task.getDescription());
      tasks.addProperty("image", url_images + "tasks/" + task.getImage());
      tasks.addProperty("xp", task.getXP());
      TarrayTask.add(tasks);
    }

    return TarrayTask;
  }

  public JsonArray getArray(Habbo habbo) {

    JsonArray InfoUser = new JsonArray();
    JsonObject info;

    try {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
      String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
          .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";

      new ManagerButtonsRight().generateRandomTask(habbo);

      LevelPlayer player = WorkSystemLevelManager.getOnlinePlayers().get(habbo.getHabboInfo().getId());

      info = new JsonObject();
      info.addProperty("username", player.getPlayerInfo().getHabbo().getHabboInfo().getUsername());
      info.addProperty("home_room_id", habboRp.checkSettings("global.home.room.rp.id", 0));
      info.addProperty("look",
          RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta").replace("%LOOK%",
              player.getPlayerInfo().getHabbo().getHabboInfo().getLook() + "")
              + "&headonly=1&size=a&gesture=sml&direction=2&head_direction=3");
      info.addProperty("level", player.getPlayerInfo().getLevel());
      info.addProperty("XP", player.getPlayerInfo().getXP());
      info.addProperty("max_XP",
          WorkSystemLevelManager.getLevelManager().Levels().get(player.getPlayerInfo().getLevel()).getXP());
      info.addProperty("icon_empleados", url_images + "jobs/" + "empleados.png");

      info.addProperty("work_room_id", habboRp.getJobActive().getRoomId());

      info.addProperty("work_name", habboRp.getJobActive().getName().replace("job_", ""));
      info.addProperty("work_name_active", habboRp.getJobActive().getName());
      info.addProperty("work_image", url_images + "jobs/job_" + habboRp.getJobActive().getName() + "_image.png?v2");
      info.addProperty("icon_base", url_images + "jobs/base.png?v2");
      info.addProperty("icon_base", url_images + "jobs/empleados.png?v2");

      InfoUser.add(info);
    } catch (Exception e) {
    }

    return InfoUser;
  }

  public void ocultWindowJob(Habbo habbo) {
    try {
      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
      if (habboRp.getHabboClient() != null) {
        habboRp.getHabboClient().send(
            new MessageManager("MessageManager", false, Outcoming.JobsWindow, null));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void showWindowJob(Habbo habbo) {
    try {
      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
      if (habboRp.getHabboClient() != null) {
        habboRp.getHabboClient().send(
            new MessageManager("MessageManager", true, Outcoming.JobsWindow, this.getDataWindow(habbo)));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public JsonArray getDataWindow(Habbo habbo) throws SQLException {

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
    String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
        .replace("%url.hotel.base%", url_hotel_base);

    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

    JsonArray TarrayTask = new JsonArray();
    JsonObject tasks = new JsonObject();
    tasks.addProperty("id", habbo.getHabboInfo().getId());
    TarrayTask.add(tasks);

    return TarrayTask;
  }

}
