package com.srth.plugin.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryItemsComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Inventorys.Items.Items;

import gnu.trove.map.hash.THashMap;

import java.util.HashMap;
import java.util.Map;

public class EmptyInventoryCommandCustomizado extends Command {
    public EmptyInventoryCommandCustomizado() {
        super("cmd_empty", Emulator.getTexts().getValue("commands.keys.cmd_empty").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        if (params.length == 1
                || (params.length == 2 && !params[1].equals(Emulator.getTexts().getValue("generic.yes")))) {
            if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                if (gameClient.getHabbo().getHabboInfo().getCurrentRoom().getUserCount() > 10) {
                    gameClient.getHabbo().alert(Emulator.getTexts().getValue("commands.succes.cmd_empty.verify")
                            .replace("%generic.yes%", Emulator.getTexts().getValue("generic.yes")));
                } else {
                    gameClient.getHabbo()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.succes.cmd_empty.verify")
                                            .replace("%generic.yes%", Emulator.getTexts().getValue("generic.yes")),
                                    RoomChatMessageBubbles.ALERT);
                }
            }

            return true;
        }

        if (params.length >= 2 && params[1].equalsIgnoreCase(Emulator.getTexts().getValue("generic.yes"))) {

            Habbo habbo = (params.length == 3 && gameClient.getHabbo().hasPermission(Permission.ACC_EMPTY_OTHERS))
                    ? Emulator.getGameEnvironment().getHabboManager().getHabbo(params[2])
                    : gameClient.getHabbo();

            if (habbo != null) {
                Map<Integer, HabboItem> items = new HashMap<>();
                THashMap<String, Items> itemsRP = new THashMap<String, Items>();

                items.putAll(habbo.getInventory().getItemsComponent().getItems());

                // Alistar solos los items que se puedan guardar al inventario
                for (Items itemsRPView : RoleplayWebsocketsRoomsInfo.getItemsManager().getItems().values()) {
                    if (itemsRPView.getInventoryStock() == 1) {
                        itemsRP.put(itemsRPView.getItemName(), itemsRPView);
                    }
                }

                // Revisar todos los items de tu inventario usando keySet() para evitar
                // problemas al remover
                for (Integer itemsHabboId : new HashMap<>(items).keySet()) {
                    HabboItem habboItem = items.get(itemsHabboId);
                    if (itemsRP.get(habboItem.getBaseItem().getName()) != null
                            || habboItem.getBaseItem().getName().startsWith("CASH_")) {
                        items.remove(itemsHabboId);
                    } else {
                        habbo.getInventory().getItemsComponent().getItems().remove(itemsHabboId);
                    }
                }

                Emulator.getThreading().run(new QueryDeleteHabboItems(items.values()));
                habbo.getClient().sendResponse(new InventoryRefreshComposer());
                habbo.getClient().sendResponse(new InventoryItemsComposer(0, 1,
                        gameClient.getHabbo().getInventory().getItemsComponent().getItems()));

                gameClient.getHabbo()
                        .whisper(
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("commands.succes.cmd_empty.cleared")
                                        .replace("%username%", habbo.getHabboInfo().getUsername()),
                                RoomChatMessageBubbles.ALERT);

            } else {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_empty"),
                        RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}