package com.srth.plugin.habbo.ButtonsExpandiblesRoom;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Actions.GoToRoomTaxi;
import com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.ConfirmSelectEventsManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import gnu.trove.map.hash.THashMap;

public class ManagerButtonsExpandiblesRoom {

  public ManagerButtonsExpandiblesRoom() {

  }

  public void show(Habbo habbo, Room room) throws SQLException {
    HabboClient client = HabboClientManager.getHabboClient(habbo);
    if (client != null) {
      client.send(new MessageManager("MessageManager", true, Outcoming.ViewButtonsExpandibles, this.getArray(habbo)));
    }
  }

  public JsonArray getArray(Habbo habbo) throws SQLException {
    JsonArray Tarray = new JsonArray();

    THashMap<Integer, JsonObject> list = new THashMap<Integer, JsonObject>();

    int rank_minimo = RoleplayWebsocketsRoomsInfo.getConfig().getInt("rank_minimo_permission_create_room");

    // Evento de prueba
    // if(habbo.getHabboInfo().getRank().getId() >= rank_minimo){
    // list.put(0, this.generateItemArray(0, "Realizar evento de prueba",
    // "EventoDePrueba", "icon-mundo"));
    // }

    // Crear salas roleplay basicas
    if (habbo.getHabboInfo().getRank().getId() >= rank_minimo) {
      list.put(1, this.generateItemArray(0, "Crear salas basicas", "ShowVentainCreateRoomBasicsRoleplay",
          "icon-info-settings"));
    }

    // list.put(2, this.generateItemArray(0, "TUTORIAL RP",
    // "viewInformacionRoleplay", "icon-info-settings"));

    list.put(3, this.generateItemArray(0, "Salir del modo RP", "exitModoRP", "icon-mundo"));

    for (JsonObject data : list.values()) {
      Tarray.add(data);
    }
    return Tarray;
  }

  public JsonObject generateItemArray(int number, String title, String event, String icon_class) {
    JsonObject itemObj;
    itemObj = new JsonObject();
    itemObj.addProperty("number", number);
    itemObj.addProperty("title", title);
    itemObj.addProperty("event", event);
    itemObj.addProperty("icon_class", icon_class);
    return itemObj;
  }

  public void incoming(THashMap<String, Object> data, HabboClient wsclient) throws SQLException {
    String get_action = ((String) data.get("action"));

    if (get_action.equalsIgnoreCase("ViewInformacionRoleplay")) {

    }

    if (get_action.equalsIgnoreCase("exitModoRP")) {
      if (wsclient != null) {

        int room_home = Emulator.getConfig().getInt("hotel.home.room");
        if (room_home != 0 && Emulator.getGameEnvironment().getRoomManager().getRoom(room_home) != null) {
          GoToRoomTaxi goToRoomTaxi = new GoToRoomTaxi();
          goToRoomTaxi.setDefaultSumatoria(15);
          goToRoomTaxi.goTo(wsclient.getHabbo(), room_home);
        } else {

          List<Room> roomsbyScore = Emulator.getGameEnvironment().getRoomManager()
              .getRoomsByScore();

          List<Room> listRooms = new ArrayList<>();

          for (Room room : roomsbyScore) {
            if (room.getCategory() == 100) {
              continue;
            }
            listRooms.add(room);
          }

          if (listRooms.isEmpty()) {
            return;
          }
          Random random = new Random();
          int randomIndex = random.nextInt(listRooms.size());
          Room randomRoom = listRooms.get(randomIndex);
          int randomRoomId = randomRoom.getId();

          GoToRoomTaxi goToRoomTaxi = new GoToRoomTaxi();
          goToRoomTaxi.setDefaultSumatoria(15);
          goToRoomTaxi.goTo(wsclient.getHabbo(), randomRoomId);
        }
      }
    }

    if (get_action.equalsIgnoreCase("ShowVentainCreateRoomBasicsRoleplay")) {

      if (wsclient != null) {
        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().showVentainCreateRooms(wsclient.getHabbo());
      }
    }

  }

}
