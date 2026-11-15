package com.srth.plugin.habbo.ConfirmEvents.confirmevents;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.TasksWorks;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import java.sql.SQLException;

public class EventConfirmFood {

    private Habbo habbo;
    private Room room;
    private String confirm_data;

    public EventConfirmFood(Habbo habbo) throws SQLException {

        this.habbo = habbo;

        if (this.habbo != null) {
            room = habbo.getHabboInfo().getCurrentRoom();

            if (room != null) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                this.confirm_data = habboRp.checkSettings("global_confirm_data", "");
                String data[] = confirm_data.replace(";", ":").split(":");
                int item_id = Integer.parseInt(data[1]);

                HabboItem habbo_item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(item_id);

                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(habbo_item.getX(), habbo_item.getY()),
                        habbo.getRoomUnit().getCurrentLocation())) {
                    return;
                }

                if (habboRp.getHabboTutorial().getCompleted() == false
                        && habboRp.getHabboTutorial().getParteTutorial() == 10
                        && habboRp.getCacheInt("platillo_primary_comido") == 1) {
                    return;
                }

                habboRp.getVidaHambreDormir().comer(40);
                habboRp.getVidaHambreDormir().ignoreHambre(6);

                habboRp.registerCache("platillo_primary_comido", "1");

                Item rewardItem = Emulator.getGameEnvironment().getItemManager().getItem("rp_comido_food_ktchn");
                HabboItem newItem = Emulator.getGameEnvironment().getItemManager()
                        .createItem(habbo.getHabboInfo().getId(), rewardItem, 0, 0, "");
                if (newItem != null) {
                    newItem.setX(habbo.getRoomUnit().getX());
                    newItem.setY(habbo.getRoomUnit().getY());
                    newItem.setZ(room.getStackHeight(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY(), false));
                    newItem.setRoomId(room.getId());
                    newItem.needsUpdate(true);
                    room.addHabboItem(newItem);
                    room.updateItem(newItem);
                    room.sendComposer(
                            new AddFloorItemComposer(newItem, room.getFurniOwnerNames().get(newItem.getUserId()))
                                    .compose());
                }

                HabboClient client = HabboClientManager.getHabboClient(habbo);
                try {
                    client.send(new PlaySound("PlaySound", true, "comer.mp3", habbo));
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                room.removeHabboItem(habbo_item);
                Emulator.getThreading().run(() -> {
                    new QueryDeleteHabboItem(habbo_item.getId()).run();
                    room.sendComposer(new RemoveFloorItemComposer(habbo_item).compose());
                    room.updateTile(room.getLayout().getTile(habbo_item.getX(), habbo_item.getY()));
                }, 500);

                // TutorialHabbo tutorialHabbo = new TutorialHabbo(habbo);
                // tutorialHabbo.checkTutorialProcessSuccess(habbo_item.getBaseItem());

                // EVENTO DE PLATILLO COMIDO
                RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos().eventComerFoodPrimary(habbo);

                // Comprobar y insertar progreso task
                new TasksWorks().CheckAndInsertTaskActive(habbo_item, habbo);

                habboRp.update("global_confirm_data", "");
            }

        }
    }

}
