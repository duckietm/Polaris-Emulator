package com.srth.plugin.habbo.ConfirmEvents.confirmevents;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class EventConfirmBoteBasura {

    private Habbo habbo;
    private Room room;

    public void updateSQLItem(int extra_data, int item_id) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE items SET `extra_data`='" + extra_data + "' WHERE `id`=" + item_id + "")) {
            statement.execute();
        } catch (SQLException e) {
        }
    }

    public EventConfirmBoteBasura(Habbo habbo) throws SQLException {
        this.habbo = habbo;

        if (this.habbo != null) {

            room = habbo.getHabboInfo().getCurrentRoom();

            if (room != null) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                String data[] = habboRp.checkSettings("global_confirm_data", "").replace(";", ":").split(":");
                int item_id = Integer.parseInt(data[1]);

                HabboItem habbo_item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(item_id);
                String name_item = habbo_item.getBaseItem().getName();

                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(habbo_item.getX(), habbo_item.getY()),
                        habbo.getRoomUnit().getCurrentLocation())) {
                    return;
                }

                // Congelar al keko y generar efecto
                habbo.getRoomUnit().setCanWalk(false);
                room.giveEffect(habbo,
                        Integer.valueOf(4),
                        2);

                // Mostrar proceso
                habboRp.getVidaHambreDormir().showTimeProcess(
                        "RoleplayWebsocketsRoomsInfo/cocinero/" + name_item + "_icon.png", "Botando basura:", 5, true);
                habboRp.setProgressPendiente(true);

                int cantidad_basura_room = habboRp.checkSettings("global_basura_room", 0);

                Emulator.getThreading().run(() -> {

                    String item_starts = "rp_comido_food";
                    int amount = 0;

                    // Eliminar todos los rp_comido_food que tengas
                    Map<Integer, HabboItem> deleted = new HashMap<>();
                    ArrayList<HabboItem> items = new ArrayList<>();
                    for (HabboItem item : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
                        if (item.getBaseItem().getName().startsWith(item_starts)) {
                            if (item.getUserId() == habbo.getHabboInfo().getId()) {
                                if ((item.getBaseItem().getName().startsWith(item_starts))) {
                                    try {
                                        items.add(item);
                                        amount += 1;
                                        habbo.getInventory().getItemsComponent().removeHabboItem(item);
                                        habbo.getClient().sendResponse(new InventoryRefreshComposer());
                                        deleted.put(item.getId(), item);
                                        Emulator.getThreading().run(new QueryDeleteHabboItems(deleted.values()));
                                    } catch (Exception e) {
                                    }
                                }
                            }
                        }
                    }

                    if (amount >= 1) {

                        habbo_item.needsUpdate(true);
                        habbo_item.setExtradata("1");
                        habbo_item.run();
                        room.updateItemState(habbo_item);
                        room.updateItem(habbo_item);
                        Emulator.getThreading().run(habbo_item);

                        int amount_total = cantidad_basura_room + amount;
                        habboRp.updateData("global_basura_room", amount_total + "");

                        // Mostrar alerta
                        THashMap<String, String> keys2 = new THashMap<>();
                        keys2.put("display", "BUBBLE");
                        keys2.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/"
                                + name_item + "_icon.png");
                        keys2.put("message",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_bote_basura.success")
                                        .replace("%br%", "\n").replace("%cantidad%", amount + ""));
                        habbo.getClient()
                                .sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                        Emulator.getThreading().run(() -> {

                            keys2.put("display", "BUBBLE");
                            keys2.put("image",
                                    "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/basura.png");
                            keys2.put("message",
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_bote_basura.show.cantidad")
                                            .replace("%br%", "\n").replace("%cantidad%", amount_total + ""));
                            habbo.getClient()
                                    .sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                        }, 2000);
                    }

                    try {
                        // Ocultar carga proceso
                        habboRp.getVidaHambreDormir().ocultarTimeProcess();

                        habboRp.getTareas().insertProgress("BotaBasuraRoom");

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    // Descongelar al keko y generar efecto
                    habbo.getRoomUnit().setCanWalk(true);
                    room.giveEffect(habbo,
                            Integer.valueOf(0),
                            -1);
                    habboRp.setProgressPendiente(false);

                }, 5000);

            }

        }
    }
}