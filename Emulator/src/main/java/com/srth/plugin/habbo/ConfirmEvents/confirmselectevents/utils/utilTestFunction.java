package com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.utils;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class utilTestFunction {

  public utilTestFunction(Habbo habbo) {
    if (RoleplayWebsocketsRoomsInfo.getConfig().getValue("name_room_prod_active").equalsIgnoreCase("true")) {

      RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().CreateNewRoomLayout(habbo,
          RoleplayWebsocketsRoomsInfo.getConfig().getValue("name_room_prod"));

    }
  }

}
