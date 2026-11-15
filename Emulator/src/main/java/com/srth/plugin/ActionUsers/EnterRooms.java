package com.srth.plugin.ActionUsers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredHandler;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.ManagerButtonsRight;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowVentDeadth;

public class EnterRooms {

  protected List<HabboItem> items_hospital;

  public EnterRooms() {
    this.items_hospital = new ArrayList<>();
  }

  public void ComprobateEnterHospitalDead(Room room, Habbo habbo) {
    try {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {

        if (room.getId() == RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("medico").getRoomId()) {

          for (HabboItem item : room.getFloorItems()) {
            if (item.getBaseItem().getName().equalsIgnoreCase("hosptl_bed")) {
              this.items_hospital.add(item);
            }
          }

          if (habboRp.getVidaHambreDormir().getVida() > 1) {
            return;
          }

          if (!this.items_hospital.isEmpty()) {
            int i = Emulator.getRandom().nextInt(this.items_hospital.size());
            HabboItem item = this.items_hospital.get(i);

            Emulator.getThreading().run(() -> {

              JsonArray array = new JsonArray();
              JsonObject jsonObject;
              jsonObject = new JsonObject();
              jsonObject.addProperty("title", "Muerto");
              jsonObject.addProperty("description",
                  RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_alert.hospital.true_hospital_action"));
              jsonObject.addProperty("msg_confirm", "Aceptar");
              array.add(jsonObject);

              try {
                habboRp.getHabboClient().send(new ShowVentDeadth("ShowVentDeadth", true, array));

                habboRp.setEnable(13);
                habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, Integer.valueOf(13), -1);

              } catch (Exception e) {
              }

              teleportUnitToTile(habbo.getRoomUnit(), room.getLayout().getTile(item.getX(), item.getY()));
            }, 250);
          }

        } else {
          Emulator.getThreading().run(() -> {
            try {
              habboRp.setCuracionPendiente(false);
            } catch (Exception e) {
            }
          }, 500);
        }
      }

    } catch (Exception e) {
    }
  }

  public void showTalkRoomName(Room room, Habbo habbo) {

    if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {

      // ocultar ITEM INFORMACION
      new ItemActionManager().OcultItemAction(habbo);

      try {

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        new ManagerClickHabbos().ocultOtherInfo(habboRp.getHabboClient());
        new ManagerClickHabbos().ocultButtonsUsarOther(habboRp.getHabboClient());

      } catch (SQLException e) {
      }

      Emulator.getThreading().run(() -> {
        habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_enter_room_rp_name")
            .replace("%ROOM_ID%", room.getId() + "").replace("%ROOM_NAME%", room.getName()));
      }, 250);
    }
  }

  public void comprobateUseTeleportTile(Room room, Habbo habbo) {
    if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
      try {

        if (habbo.getRoomUnit().isTeleporting == false) {
          return;
        }

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        if (habboRp.getUseTeleportTiles().get(room.getId()) == null) {
          habboRp.getTareas().insertProgress("UsarFlechasRP");
          habboRp.getUseTeleportTiles().put(room.getId(), room.getId());
        }
      } catch (Exception e) {
      }
    }
  }

  public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
    Room room = roomUnit.getRoom();

    if (room == null) {
      return;
    }

    // makes a temporary effect
    roomUnit.getRoom().unIdle(roomUnit.getRoom().getHabbo(roomUnit));
    room.sendComposer(new RoomUserEffectComposer(roomUnit, 4).compose());
    Emulator.getThreading().run(new SendRoomUnitEffectComposer(room, roomUnit), WiredHandler.TELEPORT_DELAY + 1000);

    if (tile == roomUnit.getCurrentLocation()) {
      return;
    }

    if (tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
      RoomTile alternativeTile = null;
      List<RoomTile> optionalTiles = room.getLayout().getTilesAround(tile);

      Collections.reverse(optionalTiles);
      for (RoomTile optionalTile : optionalTiles) {
        if (optionalTile.state != RoomTileState.INVALID && optionalTile.state != RoomTileState.BLOCKED) {
          alternativeTile = optionalTile;
          break;
        }
      }

      if (alternativeTile != null) {
        tile = alternativeTile;
      }
    }

    Emulator.getThreading().run(() -> {
      roomUnit.isWiredTeleporting = true;
    }, Math.max(0, WiredHandler.TELEPORT_DELAY - 500));
    Emulator.getThreading().run(
        new RoomUnitTeleport(roomUnit, room, tile.x, tile.y,
            tile.getStackHeight() + (tile.state == RoomTileState.SIT ? -0.5 : 0), roomUnit.getEffectId()),
        WiredHandler.TELEPORT_DELAY);
  }

  public void viewDataRoomUnit(Room room) {

    RoomLayout roomLayout = room.getLayout();
    RoleplayWebsocketsRoomsInfo.utilities().LOGGER("X: " + roomLayout.getDoorX());
    RoleplayWebsocketsRoomsInfo.utilities().LOGGER("Y: " + roomLayout.getDoorY());
    RoleplayWebsocketsRoomsInfo.utilities().LOGGER("Z: " + roomLayout.getDoorZ());
    RoleplayWebsocketsRoomsInfo.utilities().LOGGER("DIR: " + roomLayout.getDoorDirection());
  }

  public void comprobateEffect(Room room, Habbo habbo) {
    try {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      if (room != null) {

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
          return;
        }

        if (habboRp.getEnable() != 0) {
          Emulator.getThreading().run(() -> {
            try {
              room.giveEffect(habbo.getRoomUnit(), habboRp.getEnable(), -1);
            } catch (Exception e) {
            }
          }, 250);
        } else {

          // Cuando no tengas ningun enable guardado en cache pero aun asi si tengas
          // alguno activo que lo desactive

          Emulator.getThreading().run(() -> {
            try {
              room.giveEffect(habbo.getRoomUnit(), 0, -1);
            } catch (Exception e) {
            }
          }, 250);

        }

        if (habboRp.getHanditem() != 0) {
          Emulator.getThreading().run(() -> {
            try {
              room.giveHandItem(habbo.getRoomUnit(), habboRp.getHanditem());
            } catch (Exception e) {
            }
          }, 250);
        }
      }

    } catch (Exception e) {

    }
  }

  public void comprobateMyHome(Room room, Habbo habbo) {
    try {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      if (room != null) {

        habboRp.checkSettings("global.home.room.rp.id", 0);

        if (room.getId() != habboRp.getInt("global.home.room.rp.id")) {
          return;
        }

        if (habboRp.getVidaHambreDormir().duchaItemRecibido == false) {

          int countItems = 0;
          // Buscar ducha en la sala
          for (HabboItem habboItem : room.getFloorItems()) {
            if (habboItem.getBaseItem().getName().equalsIgnoreCase("bathroom_bath1")) {
              countItems++;
            }
          }

          if (countItems == 0) {

            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem("bathroom_bath1");
            HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                habbo.getHabboInfo().getId(),
                baseItem,
                0,
                0,
                "");
            habbo.getInventory().getItemsComponent().addItem(habboItem);
            habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));

            Emulator.getThreading().run(() -> {

              // Ventana alerta
              RoleplayWebsocketsRoomsInfo.utilities().showAlertUser(
                  "RoleplayWebsocketsRoomsInfo/banos/icon_banera.png", RoleplayWebsocketsRoomsInfo
                      .getTexts().getValue("action.receibed.objets.message.bano").replace("%br%", "\n"),
                  habbo.getClient());

            }, 1000);

            if (habbo.getClient() != null) {
              habbo.getClient().sendResponse(new InventoryRefreshComposer());
            }

            habboRp.updateData("ducha_item_recibido", "1");
            habboRp.getVidaHambreDormir().duchaItemRecibido = true;
          }

        }

        for (HabboItem habboItem : room.getFloorItems()) {
          if (habboItem.getBaseItem().getName().equalsIgnoreCase("ktchn_c15_stove_rp")) {

            if (habboRp.getValue("cocina_data_craft_items_active") == "none"
                || habboRp.getValue("cocina_data_craft_items_active").isEmpty()) {
              // Mostrar cocinando
              habboItem.setExtradata("1");
              habboItem.needsUpdate(true);
              habboItem.run();
              room.updateItemState(habboItem);
              room.updateItem(habboItem);
              Emulator.getThreading().run(habboItem);
              return;
            } else {
              // Mostrar cocinando
              habboItem.setExtradata("2");
              habboItem.needsUpdate(true);
              habboItem.run();
              room.updateItemState(habboItem);
              room.updateItem(habboItem);
              Emulator.getThreading().run(habboItem);
              return;
            }

          }
        }
      }

    } catch (Exception e) {

    }
  }

  public void verifiedTutorial(Room room, Habbo habbo) {
    try {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {

        Emulator.getThreading().run(() -> {

          if (habboRp.getHabboTutorial().getParteTutorial() >= 15) {
            // Mostrar botones ->
            try {
              new ManagerButtonsRight().ViewButtons(habbo);
            } catch (SQLException e) {
            }
          }
        }, 1000);

      } else {

      }

    } catch (Exception e) {
    }
  }

}
