package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectTile;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboCocinar;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionCocinar extends InteractionEffectTile implements ICycleable {

    // Definir tiempo total
    private int default_sumatoria = 20;
    private int sumatoria_total = default_sumatoria + 10;
    private int startTime = 0;
    private int roomUnitId = -1;
    private String name_item;

    public InteractionCocinar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.name_item = baseItem.getName();
    }

    public InteractionCocinar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null) {

            this.name_item = this.getBaseItem().getName();

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                return;
            }

            if (client.getHabbo().getRoomUnit().getRoomUnitType() == RoomUnitType.USER) {

                Habbo habbo = client.getHabbo();
                HabboCocinar userCocinar = new HabboCocinar(habbo);
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (habbo != null) {

                    if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                            habbo.getRoomUnit().getCurrentLocation())) {
                        return;
                    }

                    if (this.getUserId() != client.getHabbo().getHabboInfo().getId()) {
                        return;
                    }

                    if (habboRp.getProgressPendiente() == true) {
                        return;
                    }

                    if (userCocinar.getCocinando() == 1) {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/cocinero/rp_nevera_icon.png",
                                "action_cocinar.select.action", client);
                        return;
                    }

                    if (this.getExtradata().equalsIgnoreCase("0") || userCocinar.getIngredientes() >= 3) {

                        if (userCocinar.getCocinando() == 0) {

                            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
                            if (habboTutorial.getParteTutorial() < 7) {
                                return;
                            }

                            this.setExtradata("2");
                            this.needsUpdate(true);
                            room.updateItemState(this);
                            room.updateItem(this);
                            Emulator.getThreading().run(this);

                            // Mostrar proceso
                            userCocinar.showTimeProcess(
                                    "RoleplayWebsocketsRoomsInfo/cocinero/" + name_item + "_icon.png?v=1",
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.select.process"), 6,
                                    true);
                            userCocinar.setCocinando(1);
                            habboRp.setProgressPendiente(true);
                            habboRp.allowMove(false);

                            // Mostrar alerta al usuario
                            THashMap<String, String> keys2 = new THashMap<>();
                            keys2.put("display", "BUBBLE");
                            keys2.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/"
                                    + name_item + "_icon.png?v=1");
                            keys2.put("message",
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.select"));
                            habbo.getClient()
                                    .sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                            habboRp.showMessage("RoleplayWebsocketsRoomsInfo/cocinero/" + name_item + "_icon.png?v=1",
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.select"));
                            String[] getPlatillos = RoleplayWebsocketsRoomsInfo.getTexts()
                                    .getValue("action_cocinar.nombres.platillos").split(";");
                            String nombre_platillo = getPlatillos[Emulator.getRandom().nextInt(getPlatillos.length)];

                            userCocinar.setPlatillo(nombre_platillo);
                            userCocinar.setCocinando(1);

                            Emulator.getThreading().run(() -> {

                                keys2.put("display", "BUBBLE");
                                keys2.put("image",
                                        "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png?v=1");
                                keys2.put("message",
                                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.select.success")
                                                .replace("%br%", "\n").replace("%select%", nombre_platillo));
                                habbo.getClient().sendResponse(
                                        new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png?v=1",
                                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.select.success")
                                                .replace("%br%", "\n").replace("%select%", nombre_platillo));

                            }, 6000);

                            Emulator.getThreading().run(() -> {

                                if (habboRp.checkSettings("tutorial_active", 0) == 0) {
                                    keys2.put("display", "BUBBLE");
                                    keys2.put("image",
                                            "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/rp_nevera_icon.png?v=1");
                                    keys2.put("message", RoleplayWebsocketsRoomsInfo.getTexts()
                                            .getValue("action_cocinar.select.action"));
                                    habbo.getClient().sendResponse(
                                            new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));
                                }

                                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/cocinero/rp_nevera_icon.png?v=1",
                                        RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue("action_cocinar.select.action"));

                                habboRp.allowMove(true);
                                habboRp.setProgressPendiente(false);

                                RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos().eventCocinar(1,
                                        habbo);

                                // tutorialHabbo.checkTutorialProcessSuccess(this.getBaseItem());

                            }, 7500);

                        } else if (userCocinar.getCocinando() == 2) {

                            if (habboRp.getNewInventoryHabbo().getItem("ingredientes_variados") == null
                                    || habboRp.getNewInventoryHabbo().getItem("ingredientes_variados") != null
                                            && habboRp.getNewInventoryHabbo().getItem("ingredientes_variados")
                                                    .getCantidad() == 0) {
                                habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/ingredientes_variados.png",
                                        "action_cocinar.no.suf.ingredients");
                                return;
                            }

                            if (habboRp.getProgressPendiente() == true) {
                                return;
                            }

                            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
                            if (habboTutorial.getParteTutorial() < 9) {
                                return;
                            }

                            if (userCocinar.getIngredientes() >= 3) {

                                this.setExtradata("3");
                                this.needsUpdate(true);
                                room.updateItemState(this);
                                room.updateItem(this);
                                Emulator.getThreading().run(this);
                                habboRp.setProgressPendiente(true);

                                // Mostrar proceso
                                userCocinar.showTimeProcess(
                                        "RoleplayWebsocketsRoomsInfo/cocinero/" + name_item + "_icon.png?v=1",
                                        RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue("action_cocinar.process.pendient"),
                                        sumatoria_total, true);

                                this.roomUnitId = habbo.getRoomUnit().getId();

                                this.startTime = Emulator.getIntUnixTimestamp();
                            }
                        }
                    } else {

                        THashMap<String, String> notification = new THashMap<>();
                        notification.put("display", "BUBBLE");
                        notification.put("image",
                                "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/rp_ingred_cocina_1_icon.png");
                        notification.put("linkUrl", "event:navigator/goto/"
                                + RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.supermercado.id"));
                        notification.put("message",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.no.suf.ingredients2"));
                        client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, notification));
                        return;

                    }
                }
            }
        }
    }

    @Override
    public void cycle(Room room) {
        if (this.roomUnitId != -1) {

            Habbo habbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (habbo != null) {

                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                        habbo.getRoomUnit().getCurrentLocation())) {

                    try {
                        room.giveEffect(habbo,
                                Integer.valueOf(0),
                                -1);

                        HabboCocinar userCocinar;
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        userCocinar = new HabboCocinar(habbo);

                        // Ocultar
                        userCocinar.ocultarTimeProcess();
                        habboRp.setProgressPendiente(false);

                        this.reset(room);
                        this.resetTime();

                        // Quitar efecto de cocinar
                        habbo.getHabboInfo().getCurrentRoom()
                                .sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.UNKNOWN)
                                        .compose());

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                int timestamp = Emulator.getIntUnixTimestamp();

                // Crear efecto de cocinar (el usuario saluda)
                habbo.getHabboInfo().getCurrentRoom()
                        .sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.WAVE).compose());

                if (timestamp - this.startTime >= sumatoria_total) {

                    this.resetTime();

                    // habbo.getRoomUnit().setStatus(RoomUnitStatus.MOVE, 0.5 + "");
                    // Quitar efecto de cocinar
                    habbo.getHabboInfo().getCurrentRoom()
                            .sendComposer(
                                    new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.LAUGH).compose());

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        HabboCocinar userCocinar = new HabboCocinar(habbo);

                        int cantidad_platillos = RoleplayWebsocketsRoomsInfo.getConfig()
                                .getInt("cocina.cantidad.platillos");

                        // Mostrar alerta al usuario
                        THashMap<String, String> keys2 = new THashMap<>();
                        keys2.put("display", "BUBBLE");
                        keys2.put("image",
                                "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png");
                        keys2.put("message", RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.success")
                                .replace("%br%", "\n").replace("%cantidad%", cantidad_platillos + ""));
                        habbo.getClient()
                                .sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png?v=1",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.success")
                                        .replace("%br%", "\n").replace("%cantidad%", cantidad_platillos + ""));

                        Emulator.getThreading().run(() -> {
                            // Mostrar alerta ingredientes
                            keys2.put("display", "BUBBLE");
                            keys2.put("image",
                                    "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/ingredientes_variados.png");
                            keys2.put("message",
                                    RoleplayWebsocketsRoomsInfo.getTexts()
                                            .getValue("action_cocinar.success.ingredients").replace("%br%", "\n")
                                            .replace("%number_ingredientes%", "3")
                                            .replace("%total%", userCocinar.getIngredientes() + ""));
                            habbo.getClient()
                                    .sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));
                            try {
                                // ocultarTimeProcess
                                userCocinar.ocultarTimeProcess();
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }, 1000);

                        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem("rp_food_ktchn");
                        for (int i = 0; i < cantidad_platillos; i++) {
                            HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                                    habbo.getHabboInfo().getId(),
                                    baseItem,
                                    0,
                                    0,
                                    "");
                            habbo.getInventory().getItemsComponent().addItem(habboItem);

                            habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
                        }

                        habbo.getClient().sendResponse(new InventoryRefreshComposer());

                        userCocinar.setCocinando(0);
                        habboRp.setProgressPendiente(false);
                        userCocinar.restIngredientes(3);
                        userCocinar.setPlatillo("");

                        this.setExtradata("0");
                        this.needsUpdate(true);
                        room.updateItemState(this);
                        room.updateItem(this);
                        Emulator.getThreading().run(this);

                        // Insertar ITEM en inventarioRP
                        habboRp.getNewInventoryHabbo().createItem(baseItem.getName(), 1);

                        // TutorialHabbo tutorialHabbo = new TutorialHabbo(habbo);
                        // tutorialHabbo.checkTutorialProcessSuccess(this.getBaseItem());

                        // TAREA ROLEPLAY (INSERTAR)
                        habboRp.getTareas().insertProgress("UseCocineRoom");

                        RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos().eventCocinar(3, habbo);

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        this.reset(room);
    }

    private void reset(Room room) {
        this.roomUnitId = -1;
        this.startTime = 0;
        this.setExtradata("0");
        room.updateItem(this);
    }

    private void resetTime() {
        this.roomUnitId = -1;
    }

}