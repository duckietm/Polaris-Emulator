package com.srth.plugin.habbo.Inventorys;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_delitos.EventosDelitosTutorial;
import com.srth.plugin.ActionUsers.Actions.UserActionComer;
import com.srth.plugin.ActionUsers.Actions.UserActionComerManzanas;
import com.srth.plugin.ActionUsers.Actions.UserActionIngerirCafe;
import com.srth.plugin.ActionUsers.Actions.UserActionViewMyExpediente;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Delitos.HabboEncarceledManager;
import com.srth.plugin.habbo.Shooter.ShooterManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;
import gnu.trove.map.hash.THashMap;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class IncomingInventory {

    private final HabboClient wsClient;
    private final Habbo habbo;
    private final HabboRp habboRp;
    private final Room room;

    private static final Map<String, BiConsumer<IncomingInventory, THashMap<String, Object>>> actionMap = new HashMap<>();

    static {
        actionMap.put("OpenMyInventary", IncomingInventory::handleOpenInventory);
        actionMap.put("FilterItemsInventory", IncomingInventory::handleFilterItems);
        actionMap.put("EquipItem", IncomingInventory::handleEquipItem);
        actionMap.put("DesequipItem", IncomingInventory::handleDesequipItem);
        actionMap.put("EquipArmaIlegal", IncomingInventory::handleEquipArmaIlegal);
        actionMap.put("RechargBalas", IncomingInventory::handleRechargeBullets);
        actionMap.put("EquipArmaPolicia", IncomingInventory::handleEquipArmaPolicia);
        actionMap.put("EquiparLlave", IncomingInventory::handleEquipKey);
        actionMap.put("EquiparJeringa", IncomingInventory::handleEquipSyringe);
        actionMap.put("ViewExpediente", IncomingInventory::handleViewExpediente);
        actionMap.put("FoodEat", IncomingInventory::handleFoodEat);
        actionMap.put("FoodEatManzanaVerde", IncomingInventory::handleFoodEatManzanasVerdes);
        actionMap.put("FoodEatManzanaRoja", IncomingInventory::handleFoodEatManzanasRojas);

        actionMap.put("EquiparHachaMadera", IncomingInventory::handleEquipHachaMadera);
        actionMap.put("EquiparHachaPiedra", IncomingInventory::handleEquipHachaPiedra);
        actionMap.put("EquiparPicoMadera", IncomingInventory::handleEquipPicoMadera);
        actionMap.put("EquiparPicoPiedra", IncomingInventory::handleEquipPicoPiedra);

        actionMap.put("IngerirCafe", IncomingInventory::handleIngerirCafe);
        actionMap.put("EquipCanaPescar",
                (i, d) -> i.handleFishingRod("cana_basica", 1092, "Caña de pescar", "cana_basica.png"));
        actionMap.put("EquipCanaPescarIntermedia", (i, d) -> i.handleFishingRod("cana_intermedia", 2000,
                "Caña de pescar intermedia", "cana_intermedia.png"));

    }

    public IncomingInventory(THashMap<String, Object> data, HabboClient wsclient) throws SQLException {
        this.wsClient = wsclient;
        this.habbo = wsclient.getHabbo();
        this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        this.room = habbo.getHabboInfo().getCurrentRoom();

        String action = (String) data.get("action");
        if (action != null && actionMap.containsKey(action)) {
            actionMap.get(action).accept(this, data);
        }

        refreshInventoryButtons();
    }

    // --- MANEJADORES DE ACCIONES ---

    private void handleOpenInventory(THashMap<String, Object> data) {
        int type = (int) habbo.getHabboStats().cache.getOrDefault("type_inventory_view", 0);

        habboRp.getNewInventoryHabbo().showWindowInventory(type);

        if (habboRp.getNewInventoryHabbo().getCantidadNewItems() >= 1) {
            habboRp.getNewInventoryHabbo().setCantidadNewItems(0);
            refreshInventoryButtons();
        }
    }

    private void handleFilterItems(THashMap<String, Object> data) {
        int type = ((Double) data.get("date")).intValue();
        habbo.getHabboStats().cache.put("type_inventory_view", type);
        habboRp.getNewInventoryHabbo().showWindowInventory(type);
    }

    private void handleEquipItem(THashMap<String, Object> data) {

        int itemId = ((Double) data.get("date")).intValue();

        habboRp.getNewInventoryHabbo().addItemUsed(itemId);
        habboRp.getNewInventoryHabbo().updateItemsUsed();
        refreshInventoryButtons();
    }

    private void handleDesequipItem(THashMap<String, Object> data) {

        int itemId = ((Double) data.get("date")).intValue();
        habboRp.getNewInventoryHabbo().removeItemUsed(itemId);
        habboRp.getNewInventoryHabbo().updateItemsUsed();
        refreshInventoryButtons();
    }

    private void handleEquipArmaIlegal(THashMap<String, Object> data) {
        if (!isRoomValidForCombat() || isJobActiveRestricted())
            return;

        int weaponHandItem = 2002;
        boolean isEquipping = habboRp.getHanditem() != weaponHandItem;

        toggleHandItem(isEquipping ? weaponHandItem : 0, "arma ilegal", isEquipping);

        if (isEquipping) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlertItem("arma_ilegal.png", "global.success.equip.item",
                    habbo.getClient());
            habboRp.getTareas().insertProgress("EquipArmaIlegal");
            ShooterManager.equipArmaIlegal(habbo);

            new EventosDelitosTutorial().eventEquiparArmaIlegal(habbo);

        } else {
            ShooterManager.desequipArmaIlegal(habbo);
        }
        refreshInventoryButtons();
    }

    private void handleRechargeBullets(THashMap<String, Object> data) {
        if (!isRoomValidForCombat() || isJobActiveRestricted())
            return;

        ItemUsuario ammo = habboRp.getNewInventoryHabbo().getItem("municion");
        if (ammo == null || ammo.getCantidad() <= 0) {
            habboRp.getNewInventoryHabbo().removeItemUsed("municion");
            refreshInventoryButtons();
            return;
        }

        if (habboRp.getShooterHabbo().getBalas() >= 20) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/munition.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.balas_full"));
            return;
        }

        habboRp.getShooterHabbo().setBalas(20);
        habboRp.getNewInventoryHabbo().removeCantidadItem("municion", 1);

        room.getHabbos().forEach(h -> {
            try {
                HabboClient client = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(h).getHabboClient();
                if (client != null)
                    client.send(new PlaySound("PlaySound", true, "/shooter/recarga.mp3", h));
            } catch (Exception ignored) {
            }
        });

        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/munition.png",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.cartucho_municion_use"));
        refreshInventoryButtons();
    }

    private void handleFishingRod(String itemKey, int handItemId, String name, String icon) {
        if (room == null || isJobActiveRestricted())
            return;

        if (habboRp.getNewInventoryHabbo().getItem(itemKey) == null) {
            habboRp.getNewInventoryHabbo().removeItemUsed(itemKey);
            refreshInventoryButtons();
            return;
        }

        boolean isEquipping = habboRp.getHanditem() != handItemId;
        toggleHandItem(isEquipping ? handItemId : 0, name, isEquipping);

        if (isEquipping) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlertItem(icon, "cana.use.success.accion",
                    habbo.getClient());
        }
        refreshInventoryButtons();
    }

    private void handleEquipArmaPolicia(THashMap<String, Object> data) {
        if (room == null)
            return;

        if (habboRp.getJobActive() == null || !habboRp.getJobActive().getName().equalsIgnoreCase("police")) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/job_polices/police_disabled.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_polices.need.active"));
            return;
        }

        if (habboRp.checkSettings("job_polices.reporte.pendiente", 0) >= 1) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/job_polices/reporte_police_pendiente.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_police.report.pendiente"));
            return;
        }

        int policeWeapon = 2001;
        boolean isEquipping = habboRp.getHanditem() != policeWeapon;

        if (isEquipping) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlertItem("arma_policial.png",
                    "global.success.equip.item", habbo.getClient());
        }

        toggleHandItem(isEquipping ? policeWeapon : 0, "arma policial", isEquipping);
        refreshInventoryButtons();
    }

    private void handleEquipKey(THashMap<String, Object> data) {
        if (room == null || isJobActiveRestricted())
            return;

        new HabboEncarceledManager(habbo);

        int keyItem = 1035;
        boolean isEquipping = habboRp.getHanditem() != keyItem;

        toggleHandItem(isEquipping ? keyItem : 0, "llave", isEquipping);

        if (isEquipping) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlertItem("llave.png",
                    "hack_cajero_tablet.tablet.success.accion", habbo.getClient());
            habboRp.getTareas().insertProgress("EquipLlave");
        }
        refreshInventoryButtons();
    }

    private void handleEquipSyringe(THashMap<String, Object> data) {
        if (room == null)
            return;

        if (habboRp.getJobActive() == null || !habboRp.getJobActive().getName().equalsIgnoreCase("medico")) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/job_medico/work_disabled.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.need.active"));
            return;
        }

        int syringeItem = 1014;
        boolean isEquipping = habboRp.getHanditem() != syringeItem;

        if (isEquipping) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlertItem("jeringa.png",
                    "work_medico_recibe.jeringa.accion", habbo.getClient());
        }

        toggleHandItem(isEquipping ? syringeItem : 0, "jeringa", isEquipping);
        refreshInventoryButtons();
    }

    private void handleEquipHachaMadera(THashMap<String, Object> data) {
        if (room == null)
            return;

        if (habboRp.getJobActive() != null) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/security.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_work_active.delito.disabled"));
            return;
        }

        int handitem = 2003;
        boolean isEquipping = habboRp.getHanditem() != handitem;

        toggleHandItem(isEquipping ? handitem : 0, "hacha de madera", isEquipping);
        refreshInventoryButtons();
    }

    private void handleEquipHachaPiedra(THashMap<String, Object> data) {
        if (room == null)
            return;

        if (habboRp.getJobActive() != null) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/security.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_work_active.delito.disabled"));
            return;
        }

        int handitem = 2004;
        boolean isEquipping = habboRp.getHanditem() != handitem;

        toggleHandItem(isEquipping ? handitem : 0, "hacha de piedra", isEquipping);
        refreshInventoryButtons();
    }

    private void handleEquipPicoMadera(THashMap<String, Object> data) {
        if (room == null)
            return;

        if (habboRp.getJobActive() != null) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/security.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_work_active.delito.disabled"));
            return;
        }

        int handitem = 2005;
        boolean isEquipping = habboRp.getHanditem() != handitem;

        toggleHandItem(isEquipping ? handitem : 0, "pico de madera", isEquipping);
        refreshInventoryButtons();
    }

    private void handleEquipPicoPiedra(THashMap<String, Object> data) {
        if (room == null)
            return;

        if (habboRp.getJobActive() != null) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/security.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_work_active.delito.disabled"));
            return;
        }

        int handitem = 2006;
        boolean isEquipping = habboRp.getHanditem() != handitem;

        toggleHandItem(isEquipping ? handitem : 0, "pico de madera", isEquipping);
        refreshInventoryButtons();
    }

    private void handleViewExpediente(THashMap<String, Object> data) {
        if (room == null)
            return;
        new HabboEncarceledManager(habbo);
        try {
            new UserActionViewMyExpediente(habbo, 1);
        } catch (Exception e) {
        }
    }

    private void handleFoodEatManzanasVerdes(THashMap<String, Object> data) {
        try {
            new UserActionComerManzanas(habbo, 1, "verdes");
        } catch (Exception e) {
        }
        refreshInventoryButtons();
    }

    private void handleFoodEatManzanasRojas(THashMap<String, Object> data) {
        try {
            new UserActionComerManzanas(habbo, 1, "rojas");
        } catch (Exception e) {
        }
        refreshInventoryButtons();
    }

    private void handleFoodEat(THashMap<String, Object> data) {
        if (habboRp.getHabboTutorial().getParteTutorial() < 10)
            return;

        try {
            new UserActionComer(habbo, 1);
        } catch (Exception e) {
        }
        refreshInventoryButtons();
    }

    private void handleIngerirCafe(THashMap<String, Object> data) {
        try {

            InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();
            ItemUsuario itemCafe = inventoryHabboNew.getItem("cafe");
            if (itemCafe != null) {

                if (itemCafe.getCantidad() <= 0) {
                    // Mostrar mensaje
                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/dormir/cafe.png?v2",
                            "No tienes café disponibles para ingerir...");
                    return;
                }

                new UserActionIngerirCafe(habbo);
            }

        } catch (Exception e) {
        }
        refreshInventoryButtons();
    }

    // --- MÉTODOS DE UTILIDAD ---

    /**
     * Centraliza la actualización de los botones del inventario con manejo de
     * errores.
     */
    private void refreshInventoryButtons() {
        try {
            if (habboRp != null && habboRp.getNewInventoryHabbo() != null) {

                habboRp.getNewInventoryHabbo().ocultButtonsUsar(habbo);
                habboRp.getNewInventoryHabbo().showButtonsUsar(habbo);

            }
        } catch (Exception ignored) {
        }
    }

    private boolean isRoomValidForCombat() {
        if (room == null)
            return false;
        int rpCategory = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");
        int hospitalRoomId = 0;
        try {
            hospitalRoomId = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("medico").getRoomId();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (room.getCategory() != rpCategory || room.getId() == hospitalRoomId) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/security.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("avise.room_block_pvp"));
            return false;
        }

        if (RoleplayWebsocketsRoomsInfo.getHorarioManager().getTimeHorario()
                .equalsIgnoreCase("dia")) {
            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_combat_daytime_disabled"),
                    RoomChatMessageBubbles.NORMAL);
            return false;
        }
        return true;
    }

    private boolean isJobActiveRestricted() {
        if (habboRp.getJobActive() != null) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/security.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_work_active.delito.disabled"));
            return true;
        }
        return false;
    }

    private void toggleHandItem(int itemId, String objectName, boolean equip) {
        String textKey = equip ? "action_equip_objet" : "action_desequip_objet";
        habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue(textKey).replace("%OBJET_NAME%", objectName));

        Emulator.getThreading().run(() -> {
            room.giveHandItem(habbo.getRoomUnit(), itemId);
        }, 500);
        habboRp.setHanditem(itemId);
    }
}