package com.srth.plugin.habbo.Jobs;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class JobEmpleado {

  private String name_job;
  private Habbo habbo;
  private int sueldo_acumulado;
  private int tutorial_active;
  private String ultimo_look_saved;
  HabboRp habboRp;

  private int jobTimeRemaining;
  private int turnosTrabajados;

  public JobEmpleado(Habbo habbo, String name_job) throws SQLException {
    this.name_job = name_job;
    this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
    this.habbo = habbo;
    this.sueldo_acumulado = habboRp.checkSettings("job_" + name_job + ".sueldo", 0);
    this.tutorial_active = habboRp.checkSettings("tutorial_job_" + name_job, 1);
    this.ultimo_look_saved = habboRp.checkSettings("global_ultimo_look_save", "");
    this.jobTimeRemaining = habboRp.checkSettings("jobTimeRemaining", 600);
    this.turnosTrabajados = habboRp.checkSettings("job_" + name_job + ".turnosTrabajados", 0);
  }

  public void setTutorialActive(Boolean valor) {
    int set = 0;
    if (valor == true) {
      set = 1;
    }
    this.habboRp.updateData("tutorial_job_" + name_job, set + "");
    this.tutorial_active = set;
  }

  public void changeLookEmpleado(String genero) throws SQLException {
    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(this.getHabbo());
    String[] data = null;
    if (genero == "M") {
      data = habboRp.getJobActive().getLookMale().split(":");
    } else if (genero == "F") {
      data = habboRp.getJobActive().getLookFemale().split(":");
    }
    if (data.length < 2) {
      return;
    }

    String gender = data[0];
    String figure = data[1];

    if (gender.isEmpty() || figure.isEmpty() || (!gender.equalsIgnoreCase("m") && !gender.equalsIgnoreCase("f"))
        || !habbo.getHabboInfo().getGender().name().equalsIgnoreCase(gender)) {
      return;
    }

    String newFigure = "";
    for (String playerFigurePart : habbo.getHabboInfo().getLook().split("\\.")) {
      if (!playerFigurePart.startsWith("ch") && !playerFigurePart.startsWith("lg"))
        newFigure += playerFigurePart + ".";
    }

    String newFigureParts = figure;
    for (String newFigurePart : newFigureParts.split("\\.")) {
      if (newFigurePart.startsWith("hd"))
        newFigureParts = newFigureParts.replace(newFigurePart, "");
    }

    if (newFigureParts.equals(""))
      return;
    String newLook = newFigure + newFigureParts;
    if (newLook.length() > 512)
      return;

    habbo.getHabboInfo()
        .setLook(ClothingValidationManager.VALIDATE_ON_MANNEQUIN
            ? ClothingValidationManager.validateLook(habbo, newLook, habbo.getHabboInfo().getGender().name())
            : newLook);
    habboRp.getHabboClient().getHabbo().getHabboInfo().getCurrentRoom()
        .sendComposer(new RoomUserDataComposer(habbo).compose());
    habbo.getClient().sendResponse(new UserDataComposer(habbo));

  }

  public Habbo getHabbo() {
    return this.habbo;
  }

  public String getUltimoLookSaved() {
    return ultimo_look_saved;
  }

  public void pagarSueldo(int sueldo) {

    if (RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("seasonal.rol.primary.type.its.credits") == true) {
      habbo.giveCredits(sueldo);
    } else {
      this.habboRp.givePoints(sueldo);
    }

    this.habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/cash/0.png", RoleplayWebsocketsRoomsInfo.getTexts()
        .getValue("action_recibe_cash_empleo.success").replace("%br%", "\n").replace("%cantidad%", sueldo + ""));

    // Mostrar alerta
    THashMap<String, String> keys2 = new THashMap<>();
    keys2.put("display", "BUBBLE");
    keys2.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/job_medico/work_active.png");
    keys2.put("message", RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_recibe_cash_empleo.success")
        .replace("%br%", "\n").replace("%cantidad%", sueldo + ""));
    this.getHabbo().getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

    this.setSueldoAcumulado(0);
  }

  public void incrementSueldoAcumulado(int value) {
    int increment = this.getSueldoAcumulado() + value;
    this.sueldo_acumulado = increment;
    this.habboRp.updateData("job_" + name_job + ".sueldo", sueldo_acumulado + "");
  }

  public void setSueldoAcumulado(int value) {
    this.sueldo_acumulado = value;
    this.habboRp.updateData("job_" + name_job + ".sueldo", value + "");
  }

  public int getSueldoAcumulado() {
    return this.sueldo_acumulado;
  }

  public boolean getTutorialActive() {
    if (tutorial_active == 1) {
      return true;
    } else {
      return false;
    }
  }

  public int getJobTimeRemaining() {
    return jobTimeRemaining;
  }

  public void setJobTimeRemaining(int value) {
    this.jobTimeRemaining = value;
    this.habboRp.updateData("jobTimeRemaining", value + "");
  }


}
