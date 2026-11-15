package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;

import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionComidoFood extends InteractionDefault {

    public InteractionComidoFood(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionComidoFood(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (roomUnit != null && this.canWalkOn(roomUnit, room, objects)) {

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                return;
            }

            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null) {

                HabboItem item = this;

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                HabboTutorial habboTutorial = habboRp.getHabboTutorial();
                if (habboTutorial.getParteTutorial() < 12) {
                    return;
                }

                if (item.getUserId() == habbo.getHabboInfo().getId() || habboRp.getJobActive() != null
                        && habboRp.getJobActive().getName().equalsIgnoreCase("aseo")) {

                    if (item != null) {
                        room.removeHabboItem(item);
                        new QueryDeleteHabboItem(item.getId()).run();
                        room.sendComposer(new RemoveFloorItemComposer(item).compose());
                        room.updateTile(room.getLayout().getTile(item.getX(), item.getY()));
                    }

                    Emulator.getThreading().run(() -> {
                        RoomTile tile = habbo.getHabboInfo().getCurrentRoom().getLayout().getTileInFront(
                                habbo.getRoomUnit().getCurrentLocation(),
                                habbo.getRoomUnit().getBodyRotation().getValue());

                        if (tile != null && tile.isWalkable()) {
                            habbo.getRoomUnit().setGoalLocation(tile);
                        }
                    }, 300);

                    Emulator.getThreading().run(() -> {
                        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem("rp_comido_food_ktchn");
                        HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                                habbo.getHabboInfo().getId(),
                                baseItem,
                                0,
                                0,
                                "");
                        habbo.getInventory().getItemsComponent().addItem(habboItem);
                        habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
                        habbo.getClient().sendResponse(new InventoryRefreshComposer());

                        Item comprobate_item = Emulator.getGameEnvironment().getItemManager()
                                .getItem("rp_comido_food_ktchn");
                        if (comprobate_item != null) {
                            // Insertar ITEM en inventarioRP
                            habboRp.getNewInventoryHabbo().createItem(comprobate_item.getName(), 1);
                        }

                        RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos()
                                .eventRecogerPlatilloBasura(habbo);

                        // tutorialHabbo.checkTutorialProcessSuccess(this.getBaseItem());
                    }, 1000);
                }
            }
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
    }
}
