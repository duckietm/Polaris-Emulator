package com.srth.plugin.events.habboHandlers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;

public class HandlerRequestRoomLoadEvent implements ICallable {

  @Override
  public void call(MessageHandler event) {

    int roomId = event.packet.readInt();
    String password = event.packet.readString();
    Habbo habbo = event.client.getHabbo();

    if (habbo.getHabboInfo().getLoadingRoom() == 0
        && habbo.getHabboStats().roomEnterTimestamp + 500 < System.currentTimeMillis()) {

      Room room = habbo.getHabboInfo().getCurrentRoom();

      if (room != null) {
        Emulator.getGameEnvironment().getRoomManager().logExit(habbo);
        room.removeHabbo(habbo, true);
        habbo.getHabboInfo().setCurrentRoom(null);
      }

      if (habbo.getRoomUnit() != null && habbo.getRoomUnit().isTeleporting) {
        habbo.getRoomUnit().isTeleporting = false;
      }

      Emulator.getGameEnvironment().getRoomManager().enterRoom(habbo, roomId, password);
    }
  }
}