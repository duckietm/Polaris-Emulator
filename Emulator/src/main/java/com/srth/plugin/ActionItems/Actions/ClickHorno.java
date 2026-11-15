package com.srth.plugin.ActionItems.Actions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.CraftItems.ListCrafts.CraftCocineros;

public class ClickHorno {

    Item itemBase;
    HabboItem habboitem;
    Habbo habbo;

    public ClickHorno(HabboItem habboitem, Habbo habbo) {

        try {

            this.habbo = habbo;
            this.itemBase = habboitem.getBaseItem();
            this.habboitem = habboitem;

            if (habboitem.getUserId() != habbo.getHabboInfo().getId()) {
                return;
            }

            Room room = habbo.getHabboInfo().getCurrentRoom();
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            RoomTile tile = HabboItem.getSquareInFront(room.getLayout(), habboitem);
            if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(habboitem.getX(), habboitem.getY()),
                    habbo.getRoomUnit().getCurrentLocation())) {
                if (tile != null) {
                    if (tile != habbo.getHabboInfo().getCurrentRoom().getLayout().getDoorTile()) {
                        habbo.getRoomUnit().setGoalLocation(tile);
                    }
                }
            } else {
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
            }

            if (RoleplayWebsocketsRoomsInfo.getItemActionManager().comprobateCerca(habboitem, room,
                    habbo.getClient()) == false) {
                return;
            }

            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
            if (habboTutorial.getParteTutorial() < 13) {
                return;
            }

            // tutorialHabbo.checkTutorialProcessSuccess(habboitem.getBaseItem());
            new CraftCocineros().show(habbo);

        } catch (Exception e) {
        }
    }
}
