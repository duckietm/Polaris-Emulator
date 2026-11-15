package com.srth.plugin.ActionItems;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.Actions.ClickBuyIngredientes;
import com.srth.plugin.ActionItems.Actions.ClickBuyPlatillosComida;
import com.srth.plugin.ActionItems.Actions.ClickHorno;
import com.srth.plugin.ActionItems.Actions.Arboles.ArbolAction;
import com.srth.plugin.ActionItems.Actions.Basurero.BasureroAction;
import com.srth.plugin.ActionItems.Actions.Cafeteros.ColocarCafeEstanteria;
import com.srth.plugin.ActionItems.Actions.Cafeteros.OpenSacoCafe;
import com.srth.plugin.ActionItems.Actions.Cafeteros.PrepararCafe;
import com.srth.plugin.ActionItems.Actions.Cajeros.hack_cajero.*;
import com.srth.plugin.ActionItems.Actions.CofreInventorys.AbrirCofreInventory;
import com.srth.plugin.ActionItems.Actions.Pescar.Pescar;
import com.srth.plugin.ActionItems.Actions.PuertaPrision.ForzarPuertaPrision;
import com.srth.plugin.ActionItems.Actions.Rocas.RocasAction;
import com.srth.plugin.ActionItems.Actions.RocasPrision.PicarRocaPrision;
import com.srth.plugin.Rooms.Cycleables.RoomCafeteria;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.ConfirmSelectEventsManager;
import com.srth.plugin.interactions.vidayhambre.InteractionBasuraUrban;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemIncomingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemIncomingHandler.class);

    public static void process(ItemActionManager manager, String action, Habbo habbo, Room room, int itemId,
            GameClient client) {
        if (room == null)
            return;

        switch (action) {
            case "ForzarCajeroAutomatico":
                new ForzarCajeroAutomatico(room.getHabboItem(itemId), habbo);
                if (ItemActionManager.getCajeroAbierto(habbo) != null)
                    ItemActionManager.getCajeroAbierto(habbo).ocultVentana(habbo);
                break;
            case "AbrirCofreInventoryHabbo":

                new AbrirCofreInventory(room.getHabboItem(itemId), habbo);
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);

                break;

            case "PicarRocaPrision":
                handlePrisionRoca(habbo, room, itemId);
                break;

            case "actionForzarPuertaPrision":
                handlePrisionGanzuaPuerta(habbo, room, itemId);
                break;

            case "AbrirCajeroAutomatico":
                HabboItem atm = room.getHabboItem(itemId);
                if (!manager.comprobateCerca(atm, room, client)) {
                    manager.moverHabboFrontObjet(atm, habbo);
                } else if (ItemActionManager.getCajeroAbierto(habbo) == null) {
                    OpenCajero open = new OpenCajero(atm, habbo);
                    ItemActionManager.addCajero(habbo, open);
                    open.showVentana(habbo);
                }
                break;

            case "AbrirPuertaHome":
                HabboItem door = room.getHabboItem(itemId);
                if (door != null) {
                    try {
                        door.onClick(client, room, new Object[] { 1 });
                    } catch (Exception e) {
                    }
                    RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos().eventOpenPuertaHome(habbo);
                }
                break;
            case "AbrirPuertaPrision":
                HabboItem doorPrision = room.getHabboItem(itemId);
                if (doorPrision != null) {
                    try {
                        doorPrision.onClick(client, room, new Object[] { 1 });
                    } catch (Exception e) {
                    }
                }
                break;

            case "TirarCanaPescar":
                handlePesca(habbo, room, itemId, manager);
                break;

            case "actionObtenerJeringa":
                handleJeringa(habbo);
                break;

            case "actionComprarCafe":
                handleCafe(habbo, room, itemId, "comprar", manager);
                break;
            case "actionTomarGranosCafe":
                HabboItem granosCafeItem = room.getHabboItem(itemId);
                if (!manager.comprobateCerca(granosCafeItem, room, client)) {
                    manager.moverHabboFrontObjet(granosCafeItem, habbo);
                    return;
                } else if (OpenSacoCafe.users.get(habbo) == null) {

                    OpenSacoCafe actionTimer = new OpenSacoCafe(granosCafeItem, habbo);
                    OpenSacoCafe.users.put(habbo, actionTimer);

                    return;
                }
                break;
            case "actionPrepararCafe":
                HabboItem cafeteraItem = room.getHabboItem(itemId);
                if (!manager.comprobateCerca(cafeteraItem, room, client)) {
                    manager.moverHabboFrontObjet(cafeteraItem, habbo);
                    return;
                } else if (PrepararCafe.users.get(habbo) == null) {
                    PrepararCafe actionTimer = new PrepararCafe(cafeteraItem, habbo);
                    PrepararCafe.users.put(habbo, actionTimer);
                    return;
                }
                break;
            case "actionColocarCafeEstanteria":
                HabboItem estanteriaCafeItem = room.getHabboItem(itemId);
                if (!manager.comprobateCerca(estanteriaCafeItem, room, client)) {
                    manager.moverHabboFrontObjet(estanteriaCafeItem, habbo);
                    return;
                } else if (ColocarCafeEstanteria.users.get(habbo) == null) {
                    ColocarCafeEstanteria actionTimer = new ColocarCafeEstanteria(estanteriaCafeItem, habbo);
                    ColocarCafeEstanteria.users.put(habbo, actionTimer);
                    return;
                }
                break;
            case "actionRecogerBasuraUrban":
                HabboItem itemClick = room.getHabboItem(itemId);
                if (itemClick instanceof InteractionBasuraUrban) {
                    InteractionBasuraUrban interactionBasuraUrban = (InteractionBasuraUrban) itemClick;
                    interactionBasuraUrban.eventClick(client, room);
                }
                break;

            case "actionBuscarBasuraUrbana":
                HabboItem itemClickBuscar = room.getHabboItem(itemId);
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
                new BasureroAction(itemClickBuscar, habbo);
                break;

            case "actionComprarComidaMaquina":
                HabboItem itemClickComida = room.getHabboItem(itemId);
                if (itemClickComida != null) {
                    new ClickBuyPlatillosComida(itemClickComida, habbo);
                }
                break;
            case "actionComprarIngredientes":
                HabboItem itemClickIngredientes = room.getHabboItem(itemId);
                if (itemClickIngredientes != null) {
                    new ClickBuyIngredientes(itemClickIngredientes, habbo);
                }
                break;
            case "actionOpenCofreMadera":
                HabboItem itemClickCofre = room.getHabboItem(itemId);
                if (itemClickCofre != null) {
                    RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
                    RoleplayWebsocketsRoomsInfo.getCofreInventoryManager().openCofreInventory(habbo, itemClickCofre);
                }
                break;
            case "actionTalarArbolMadera":
                HabboItem itemClickArbol = room.getHabboItem(itemId);
                if (itemClickArbol != null) {
                    RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
                    new ArbolAction(itemClickArbol, habbo);
                }
                break;
            case "actionCocinarEnHorno":
                HabboItem itemClickHorno = room.getHabboItem(itemId);
                if (itemClickHorno != null) {
                    new ClickHorno(itemClickHorno, habbo);
                }
                break;
            case "actionMinarRoca":
                HabboItem itemMinarRoca = room.getHabboItem(itemId);
                if (itemMinarRoca != null) {
                    new RocasAction(itemMinarRoca, habbo);
                }
                break;
        }
    }

    private static void handlePrisionRoca(Habbo habbo, Room room, int itemId) {
        try {
            HabboRp rp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (!rp.getDelitosHabbo().getEncarceled())
                return;
            HabboItem item = room.getHabboItem(itemId);
            if (item == null)
                return;

            ItemActionManager.addRoca(habbo, new PicarRocaPrision(item, habbo));

        } catch (Exception e) {
        }
    }

    private static void handlePrisionGanzuaPuerta(Habbo habbo, Room room, int itemId) {
        try {
            HabboRp rp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (rp == null || !rp.getDelitosHabbo().getEncarceled())
                return;
            HabboItem item = room.getHabboItem(itemId);
            if (item == null)
                return;

            ForzarPuertaPrision currentAction = ItemActionManager.getForzarPuertaPrision(habbo);
            if (currentAction != null && !currentAction.isDisposed())
                return;

            new ForzarPuertaPrision(item, habbo);

        } catch (Exception e) {
            LOGGER.error("Failed to start prison door lockpicking for {}", habbo.getHabboInfo().getUsername(), e);
        }
    }

    private static void handleCafe(Habbo habbo, Room room, int itemId, String type, ItemActionManager manager) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboItem item = room.getHabboItem(itemId);
            if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                    habbo.getRoomUnit().getCurrentLocation())) {
                manager.moverHabboFrontObjet(item, habbo);
                return;
            }

            if (type.equals("comprar")) {
                if (RoomCafeteria.getEstadoEstante() == 0) {
                    habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/dormir/cafe.png",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("avise_cafe_empty"));
                } else {

                    int costo_cafe = 5;
                    if (habboRp.getDineroMonedero() <= 5) {
                        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/cash/2.png",
                                "No tiene suficiente dinero para comprar cafe, necesitas %costo% cash.".replace(
                                        "%costo%",
                                        costo_cafe + ""));
                        return;
                    }

                    new ConfirmSelectEventsManager().showConfirmAlert(habbo, "confirmBuyCafeCafeteria");

                }
            }
        } catch (Exception e) {
        }
    }

    private static void handlePesca(Habbo habbo, Room room, int itemId, ItemActionManager manager) {
        try {
            HabboRp rp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboItem item = room.getHabboItem(itemId);
            manager.moverHabboFrontObjet(item, habbo);
            if (rp.getHanditem() != 1092 && rp.getHanditem() != 2000)
                return;
            if (ItemActionManager.getPescar().get(habbo) == null) {
                ItemActionManager.getPescar().put(habbo, new Pescar(item, habbo));
            }
        } catch (Exception e) {
        }
    }

    private static void handleJeringa(Habbo habbo) {
        try {
            HabboRp rp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (rp.getNewInventoryHabbo().getItem("jeringa") == null)
                rp.getNewInventoryHabbo().addCantidadItem("jeringa", 1);
            new EventosEmpleosTutorial(habbo).eventTomarJeringa();
        } catch (Exception e) {
        }
    }
}