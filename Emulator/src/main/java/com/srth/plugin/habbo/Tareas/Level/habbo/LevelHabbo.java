package com.srth.plugin.habbo.Tareas.Level.habbo;

import java.sql.SQLException;
import java.util.Map;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Tareas.Level.Level;
import com.srth.plugin.habbo.Tareas.Level.Reward;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDoubleObjet;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class LevelHabbo {

  private int habbo_id;
  private int level;
  private int XP;
  private Habbo habbo;
  private HabboRp habboRp;
  THashMap<Integer, Integer> premiosWon = new THashMap<Integer, Integer>();
  THashMap<Integer, Integer> premiosWonVIP = new THashMap<Integer, Integer>();

  String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
  String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
      .replace("%url.hotel.base%", url_hotel_base);

  public LevelHabbo(int user_id) throws SQLException {
    this.habbo_id = user_id;

    if (Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id) != null) {
      this.habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id);
      this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
    } else {
      this.habbo = null;
      this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRpOffline(user_id);
    }

    this.level = habboRp.checkSettings("level_rp.level", 1);
    this.XP = habboRp.checkSettings("level_rp.xp", 1);

    habboRp.checkSettings("premios_obt_json_array", "[]");
    habboRp.checkSettings("premios_VIP_obt_json_array", "[]");
  }

  public void loadPremios() {

    this.premiosWon.clear();

    JsonParser parser = new JsonParser();
    JsonArray gsonArr = parser
        .parse(habboRp.getValue("premios_obt_json_array"))
        .getAsJsonArray();
    if (gsonArr.size() >= 1) {
      for (JsonElement obj : gsonArr) {
        // Object of array
        JsonObject gsonObj = obj.getAsJsonObject();
        // Primitives elements of object
        int level = gsonObj.get("level").getAsInt();
        int premios = gsonObj.get("premios").getAsInt();
        this.premiosWon.put(level, premios);
      }
    }
  }

  public void loadPremiosVIP() {

    this.premiosWonVIP.clear();

    JsonParser parser = new JsonParser();
    JsonArray gsonArr = parser
        .parse(habboRp.getValue("premios_VIP_obt_json_array"))
        .getAsJsonArray();

    if (gsonArr.size() >= 1) {
      for (JsonElement obj : gsonArr) {
        // Object of array
        JsonObject gsonObj = obj.getAsJsonObject();
        // Primitives elements of object
        int level = gsonObj.get("level").getAsInt();
        int premios = gsonObj.get("premios").getAsInt();

        this.premiosWon.put(level, premios);
      }
    }

  }

  public Boolean comprobateNextLevel(Level levelComprobate) {
    try {
      if (this.getXP() >= levelComprobate.getXP() || this.getLevel() >= levelComprobate.getLevel()) {
        if (this.getPremiosWon().get(levelComprobate.getLevel()) == null) {
          return true;
        } else {
          return false;
        }
      } else {
        return false;
      }
    } catch (Exception e) {
    }
    return false;
  }

  public Boolean comprobateNextLevelVip(Level levelComprobate) {
    try {

      if (habboRp.getVip() == false) {
        return false;
      }

      if (this.getXP() >= levelComprobate.getXP() || this.getLevel() >= levelComprobate.getLevel()) {

        if (this.getPremiosWonVIP().get(levelComprobate.getLevel()) == null) {
          return true;
        } else {
          return false;
        }

      } else {
        return false;
      }
    } catch (Exception e) {
    }
    return false;
  }

  public void registerPremios(int level, int count) {
    premiosWon.put(level, count);

    Gson gson = new Gson();
    JsonArray jsonArray = new JsonArray();
    for (Map.Entry<Integer, Integer> entry : premiosWon.entrySet()) {
      JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("level", entry.getKey());
      jsonObject.addProperty("premios", entry.getValue());
      jsonArray.add(jsonObject);
    }

    String jsonString = gson.toJson(jsonArray);

    habboRp.updateData("premios_obt_json_array", jsonString);
    this.loadPremios();
  }

  public void registerPremiosVIP(int level, int count) {

    premiosWonVIP.put(level, count);

    Gson gson = new Gson();
    JsonArray jsonArray = new JsonArray();
    for (Map.Entry<Integer, Integer> entry : premiosWonVIP.entrySet()) {
      JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("level", entry.getKey());
      jsonObject.addProperty("premios", entry.getValue());
      jsonArray.add(jsonObject);
    }

    String jsonString = gson.toJson(jsonArray);

    habboRp.updateData("premios_VIP_obt_json_array", jsonString);
    this.loadPremiosVIP();
  }

  public THashMap<Integer, Integer> getPremiosWon() {
    return premiosWon;
  }

  public THashMap<Integer, Integer> getPremiosWonVIP() {
    return premiosWonVIP;
  }

  public void resetPremiosWon() {

    habboRp.updateData("premios_obt_json_array", "[]");
    this.loadPremios();

  }

  public void resetPremiosWonVIP() {
    habboRp.updateData("premios_vip_obt_json_array", "[]");
    this.loadPremios();
  }

  public void showWindow(JsonArray array) {
    try {
      this.getHabboRp().getHabboClient()
          .send(new MessageManager("MessageManager", true, Outcoming.ViewLevelHabboWindow, array));
    } catch (Exception e) {
    }
  }

  public JsonArray getArrayData() {

    JsonArray Array = new JsonArray();
    JsonObject jsonObject2;
    jsonObject2 = new JsonObject();
    jsonObject2.addProperty("username", this.getHabbo().getHabboInfo().getUsername());

    String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
        + "&headonly=0&size=a&gesture=sml&head_direction=2";

    jsonObject2.addProperty("look", look.replace("%LOOK%", this.getHabbo().getHabboInfo().getLook()));
    try {
      Level proximo_level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(this.getLevel() + 1);
      jsonObject2.addProperty("xp_required", proximo_level.getXP());
    } catch (Exception e) {
    }

    jsonObject2.addProperty("xp", this.getXP());
    jsonObject2.addProperty("level", this.getLevel());

    int totalMinutos = habboRp.getTimeOnline();
    int dias = totalMinutos / (24 * 60); // 1 día = 24 horas * 60 minutos
    int minutosRestantes = totalMinutos % (24 * 60);
    int horas = minutosRestantes / 60;
    int minutos = minutosRestantes % 60;

    String total_time = RoleplayWebsocketsRoomsInfo.getTexts().getValue("tiempo_dias_horas_minutos")
        .replace("%dias%", dias + "").replace("%horas%", horas + "").replace("%minutos%", minutos + "");

    jsonObject2.addProperty("time_online_minutos", habboRp.getTimeOnline());
    jsonObject2.addProperty("time_online_convertido", total_time);
    jsonObject2.addProperty("dinero", habbo.getHabboInfo().getCurrencyAmount(2));

    jsonObject2.addProperty("veces_arrestado", habboRp.getDelitosHabbo().getArrestos());
    jsonObject2.addProperty("delitos_cometidos", habboRp.getDelitosHabbo().getDelitos());

    jsonObject2.addProperty("estado_prision",
        habboRp.getDelitosHabbo().getEncarceled() == true ? "En prision" : "Libre");

    jsonObject2.addProperty("empleo_activo",
        habboRp.getJobActive() == null ? "Ninguno" : habboRp.getJobActive().getName().toUpperCase());

    Array.add(jsonObject2);
    return Array;
  }

  public JsonArray getArrayOffline() {

    JsonArray Array = new JsonArray();
    JsonObject jsonObject2;
    jsonObject2 = new JsonObject();
    jsonObject2.addProperty("username", HabboManager.getOfflineHabboInfo(habbo_id).getUsername());

    String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
        + "&headonly=0&size=a&gesture=sml&head_direction=2";

    jsonObject2.addProperty("look", look.replace("%LOOK%", HabboManager.getOfflineHabboInfo(habbo_id).getLook()));
    try {
      Level proximo_level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(this.getLevel() + 1);
      jsonObject2.addProperty("xp_required", proximo_level.getXP());
    } catch (Exception e) {
    }

    jsonObject2.addProperty("xp", this.getXP());
    jsonObject2.addProperty("level", this.getLevel());

    int totalMinutos = habboRp.getTimeOnline();
    int dias = totalMinutos / (24 * 60); // 1 día = 24 horas * 60 minutos
    int minutosRestantes = totalMinutos % (24 * 60);
    int horas = minutosRestantes / 60;
    int minutos = minutosRestantes % 60;

    String total_time = RoleplayWebsocketsRoomsInfo.getTexts().getValue("tiempo_dias_horas_minutos")
        .replace("%dias%", dias + "").replace("%horas%", horas + "").replace("%minutos%", minutos + "");

    jsonObject2.addProperty("time_online_minutos", habboRp.getTimeOnline());
    jsonObject2.addProperty("time_online_convertido", total_time);
    jsonObject2.addProperty("dinero", HabboManager.getOfflineHabboInfo(habbo_id).getCurrencyAmount(2));

    jsonObject2.addProperty("veces_arrestado", habboRp.getValueOffline("arrestos"));
    jsonObject2.addProperty("delitos_cometidos", habboRp.getValueOffline("delitos"));

    jsonObject2.addProperty("estado_prision",
        habboRp.getValueOffline("its.encarceled").equalsIgnoreCase("1") ? "En prision" : "Libre");

    jsonObject2.addProperty("empleo_activo",
        habboRp.getJobActive() == null ? "Ninguno" : habboRp.getJobActive().getName().toUpperCase());

    Array.add(jsonObject2);
    return Array;
  }

  public void insertXp(int value) {
    try {
      int proximo_nivel = this.getLevel() + 1;
      Level level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(proximo_nivel);
      if (level != null) {
        int suma = this.getXP() + value;
        if (suma >= level.getXP()) {

          suma = 0;
          this.XP = suma;
          habboRp.updateData("level_rp.xp", suma + "");
          this.incrementLevel();

        } else {
          this.XP = suma;
          habboRp.updateData("level_rp.xp", suma + "");
        }
      }
    } catch (Exception e) {
    }
  }

  public void incrementLevel() {
    try {
      int proximo_nivel = this.getLevel() + 1;
      Level level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(proximo_nivel);
      if (level != null) {
        this.level = proximo_nivel;
        habboRp.updateData("level_rp.level", proximo_nivel + "");
      }
    } catch (Exception e) {
    }
  }

  public void obtenerRewards(Level levelOb, Boolean vip) {
    try {

      habboRp.getTareas().ocultTareasWindow();

      habboRp.getHabboClient().send(new PlaySound("PlaySound", true, "new_level.mp3", habbo));

      habboRp.getHabboClient().send(new MessageManagerDoubleObjet("MessageManager", true, Outcoming.ObtenerPremioLevel,
          this.getArrayData(), this.getLevelObjetInfo(levelOb, vip)));

      THashMap<Integer, Reward> rewardsList = null;
      if (vip == false) {
        rewardsList = levelOb.getRewards();

        habboRp.getLevelHabbo().registerPremios(levelOb.getLevel(), rewardsList.size());
      } else {
        rewardsList = levelOb.getRewardsVip();

        habboRp.getLevelHabbo().registerPremiosVIP(levelOb.getLevel(), rewardsList.size());
      }

      for (Reward reward : rewardsList.values()) {
        if (reward.getExist() == 1) {

          if (reward.getName().equalsIgnoreCase("CASH_1_money_cash_by_th")) {
            // Agregar monedas al canje
            habboRp.givePoints(reward.getCantidad());
          } else {
            // Agregar items al inventario normal
            for (int i = 0; i < reward.getCantidad(); i++) {
              Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(reward.getName());
              HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                  habbo.getHabboInfo().getId(),
                  baseItem,
                  0,
                  0,
                  "");
              habbo.getInventory().getItemsComponent().addItem(habboItem);
              habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
            }
          }

        } else {
          // Agregar items al inventario RP
          for (int i = 0; i < reward.getCantidad(); i++) {
            habboRp.getNewInventoryHabbo().addCantidadItem(reward.getName(), reward.getCantidad());
          }
        }
      }
    } catch (Exception e) {
    }
  }

  public JsonObject getLevelObjetInfo(Level level, Boolean vip) {

    JsonObject Objeto = new JsonObject();
    JsonArray levelArray = new JsonArray();

    JsonArray arrayType = new JsonArray();
    JsonObject jsonObject = new JsonObject();

    JsonArray info = new JsonArray();
    JsonObject jsonObjectInfo = new JsonObject();
    jsonObjectInfo.addProperty("level", level.getLevel());
    jsonObjectInfo.addProperty("title", "Premios por subir a nivel:");
    jsonObjectInfo.addProperty("desc",
        "Obtuviste %value% premios por subir de nivel".replace("%value%", level.getRewards().size() + ""));

    THashMap<Integer, Reward> rewardsList = null;
    if (vip == false) {
      rewardsList = level.getRewards();
      jsonObjectInfo.addProperty("img",
          global_url_images + "media/reward.png");
    } else {
      rewardsList = level.getRewardsVip();
      jsonObjectInfo.addProperty("img",
          global_url_images + "media/reward_vip.png");
    }
    info.add(jsonObjectInfo);
    jsonObject.add("info", info);

    JsonArray rewards = new JsonArray();
    for (Reward reward : rewardsList.values()) {
      JsonObject jsonObject2 = new JsonObject();

      String url_furni_exist = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
          .replace("%url.hotel.base%", url_hotel_base) + "icons/" + reward.getName() + "_icon.png";

      jsonObject2.addProperty("name", reward.getName());
      jsonObject2.addProperty("title", reward.getTitle());
      jsonObject2.addProperty("descripcion", reward.getDescripcion());
      jsonObject2.addProperty("cantidad", reward.getCantidad());
      if (reward.getExist() == 1) {
        jsonObject2.addProperty("imagen", url_furni_exist);
      } else {
        jsonObject2.addProperty("imagen",
            global_url_images
                + "items/" + reward.getName() + ".png");
      }
      rewards.add(jsonObject2);
    }
    jsonObject.add("rewards", rewards);
    arrayType.add(jsonObject);

    levelArray.add(arrayType);

    Objeto.add("level", levelArray);

    return Objeto;
  }

  public HabboRp getHabboRp() {
    return this.habboRp;
  }

  public Habbo getHabbo() {
    return this.habbo;
  }

  public int getHabboId() {
    return this.habbo_id;
  }

  public int getLevel() {
    return this.level;
  }

  public int getXP() {
    return this.XP;
  }

}
