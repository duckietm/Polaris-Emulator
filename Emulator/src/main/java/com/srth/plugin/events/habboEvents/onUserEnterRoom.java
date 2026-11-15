package com.srth.plugin.events.habboEvents;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserEnterRoomEvent;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.TutorialHabboRpManager;
import com.srth.plugin.ActionUsers.EnterRooms;
import com.srth.plugin.bots.BotsManager;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.ButtonsExpandiblesRoom.ManagerButtonsExpandiblesRoom;
import com.srth.plugin.habbo.Delitos.PolicesManager;
import com.srth.plugin.habbo.Delitos.PoliceTransportManager;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Shooter.ShooterManager;
import com.srth.plugin.habbo.VidaHambreDormir.VidaHambreDormir;
import com.srth.plugin.utilities.utils.comprobateUsePlugin;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowInfoMyHabbo;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutShowUserActions;

public class onUserEnterRoom implements EventListener {

    @EventHandler
    public static void eventUserEnterRoom(UserEnterRoomEvent event) throws SQLException {

        if (event.habbo != null) {

            final Room room = event.room;
            PoliceTransportManager.onUserEnterRoom(event.habbo, room);
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);

            if(habboRp == null){
                return;
            }

            final Habbo habbo = event.habbo;
            final HabboClient client = habboRp.getHabboClient();

            if (client == null) {
                return;
            }

            if (room != null) {

                if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {

                    // Emulator.getThreading().run(() -> {
                    //     try {

                    //         int id_room_vieja = room.getId();

                    //         if (id_room_vieja == habbo.getHabboInfo().getHomeRoom()) {

                    //             String dataRoomTileSave = habboRp.getValue("dataRoomTileSave");
                    //             if (dataRoomTileSave != "none" || !dataRoomTileSave.isEmpty()) {

                    //                 String[] parts = dataRoomTileSave.split(";");
                    //                 short x = Short.parseShort(parts[0].replace("x:", ""));
                    //                 short y = Short.parseShort(parts[1].replace("y:", ""));
                    //                 // short z = Short.parseShort(parts[2].replace("z:", ""));

                    //                 RoomTile roomtile = room.getLayout().getTile(x, y);
                    //                 if (room.getLayout().getTile(x, y) == null) {
                    //                     RoomLayout roomLayout = room.getLayout();
                    //                     Short shortZ = (short) roomLayout.getDoorZ();
                    //                     roomtile = new RoomTile(roomLayout.getDoorX(), roomLayout.getDoorY(),
                    //                             shortZ, RoomTileState.OPEN, true);
                    //                 }

                    //                 if (roomtile != null) {
                    //                     habbo.getRoomUnit().setLocation(roomtile);
                    //                 }

                    //             } else {
                    //                 habboRp.updateData("dataRoomTileSave", "");
                    //                 RoomLayout roomLayout = room.getLayout();
                    //                 Short shortZ = (short) roomLayout.getDoorZ();
                    //                 RoomTile roomtile = room.getLayout().getDoorTile();
                    //                 habbo.getRoomUnit().setLocation(roomtile);
                    //             }
                    //         } else {
                    //             habboRp.updateData("dataRoomTileSave", "");
                    //         }
                    //     } catch (Exception e) {
                    //     }
                    // }, 100);

                    Emulator.getThreading().run(() -> {
                        try {

                            RoleplayWebsocketsRoomsInfo.getConsolaManager().showConsola(habbo);

                        } catch (Exception e) {
                        }
                    }, 1000);

                    Emulator.getThreading().run(() -> {
                        try {
                            // Establecer empleo
                            String name_ultimo_job = habboRp.getValue("ultimo_job_active");

                            Job jobActivate = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(name_ultimo_job);
                            if (jobActivate != null) {
                                if (jobActivate.getEmpleado(event.habbo) == null) {
                                    jobActivate.setEmpleado(event.habbo);

                                    jobActivate.getEmpleado(event.habbo)
                                            .changeLookEmpleado(
                                                    habboRp.getHabbo().getHabboInfo().getGender().toString());

                                    habboRp.setJobActive(jobActivate);
                                    RoleplayWebsocketsRoomsInfo.getJobsManager().showMenuJob(habbo);

                                }
                            } else {


                                habboRp.updateData("global_ultimo_look_save", habbo.getHabboInfo().getLook());

                                String ultimo_look = habboRp.getValue("global_ultimo_look_save");
                                habboRp.getHabbo().getHabboInfo().setLook(ultimo_look);

                            }
                        } catch (Exception e) {
                        }

                    }, 500);

                    comprobateUsePlugin.comprobateShutdown();

                    TutorialHabboRpManager tutorialHabboManager = RoleplayWebsocketsRoomsInfo
                            .getTutorialHabboRpManager();
                    tutorialHabboManager.userEnterRoom(habbo);

                    EnterRooms enterRooms = new EnterRooms();
                    enterRooms.ComprobateEnterHospitalDead(room, habbo);
                    enterRooms.showTalkRoomName(room, habbo);

                    // enterRooms.verifiedTutorial(room, habbo);

                    enterRooms.comprobateMyHome(room, habbo);
                    enterRooms.comprobateEffect(room, habbo);
                    enterRooms.comprobateUseTeleportTile(room, habbo);

                    BotsManager.userEnterRoom(room, habbo);
                    ShooterManager.userEnterRoom(room, habbo);

                    // Nuevo controlador de usar taxi
                    RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi().userEnterRoom(room, habbo);

                    // Ocultar resta temporizador
                    client.send(new MessageManager("MessageManager", false, Outcoming.TimeEsperaUse,
                            null));

                    RoleplayWebsocketsRoomsInfo.getHorarioManager().showDiaTardeNoche(habbo);

                    // Mostrar informacion de la sala
                    RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().showInfoSala(habbo, room);

                    habboRp.getHabboRetos().eventEnterRoom(habbo);

                    if (habboRp.getHabboTutorial().getCompleted() == true) {

                        if (habboRp.getConstructorSalas() == true) {
                            // Comenzar interval y mostrar divs
                            habboRp.getHabboClient()
                                    .send(new MessageManager("MessageManager", false, Outcoming.ExitRoom,
                                            null));
                        } else {
                            // Comenzar interval y ocultar divs
                            habboRp.getHabboClient()
                                    .send(new MessageManager("MessageManager", true, Outcoming.EnterRoomRP,
                                            null));
                        }
                    } else {
                        // Comenzar interval y ocultar divs
                        habboRp.getHabboClient()
                                .send(new MessageManager("MessageManager", false, Outcoming.ExitRoom,
                                        null));
                    }

                    habboRp.getTareas().showButtonTareas();
                    habboRp.getTareas().loadProgresos();

                    // Encarcelamiento
                    new PolicesManager().ComprobateEnterCarcelPrisionero(room, habbo);

                    new ManagerButtonsExpandiblesRoom().show(habbo, room);

                    VidaHambreDormir vidaHambreDormir = habboRp.getVidaHambreDormir();
                    // Mostrar Informacion Vida si o si
                    vidaHambreDormir.renderStats();
                    client.send(new ShowInfoMyHabbo("ShowInfoMyHabbo", true, vidaHambreDormir.getArray()));

                    // Mostrar botones de usar
                    habboRp.getNewInventoryHabbo().showButtonsUsar(habbo);

                    // Reiniciar el asesinato reciente
                    if (room.getId() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.hospital.id")) {
                        if (habboRp.getValue("asesined") != "none") {
                            habboRp.update("asesined", "0");
                            habboRp.update("asesined_by", "");
                        }
                    }
                } else if (room.getCategory() != 100) {

                    // Cuando tenias un empleo activo pero sales de las salas roleplay
                    String name_ultimo_job = habboRp.getValue("ultimo_job_active");
                    Job jobUltimo = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(name_ultimo_job);
                    if (jobUltimo != null) {
                        if (jobUltimo.getEmpleado(event.habbo) != null) {

                            jobUltimo.removeEmpleado(event.habbo);
                            jobUltimo.resetLook(event.habbo);

                        }
                    }

                    client.send(new MessageManager("MessageManager", false, Outcoming.InventoryButtons, null));
                    client.send(new OutShowUserActions("ShowUserActions", false, null));

                    client.send(new ShowInfoMyHabbo("ShowInfoOtherHabbo", false, null));
                    client.send(new ShowInfoMyHabbo("ShowInfoMyHabbo", false, null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.ShowInfoSala, null));

                    client.send(new MessageManager("MessageManager", false, Outcoming.TareasFranButton, null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.TareasFranWindow, null));

                    client.send(new MessageManager("MessageManager", false, Outcoming.SuccessTarea, null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.ObtenerPremioLevel, null));

                    client.send(new MessageManager("MessageManager", false, Outcoming.TutorialViewDesplegableEmpleos,
                            null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.TutorialViewDesplegable,
                            null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.RetosTutorialActiveRight,
                            null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.RetosTutorial,
                            null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.RetosTutorialActiveRight,
                            null));
                    client.send(new MessageManager("MessageManager", false, Outcoming.GestorDeSalasWindow,
                            null));

                    RoleplayWebsocketsRoomsInfo.getConsolaManager().ocultConsola(habbo);
                    RoleplayWebsocketsRoomsInfo.getTurnosManager().ocultTimeTurnos(habbo);

                }
            }

        }

    }

    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        if (roomUnit == null || tile == null || roomUnit.isWiredTeleporting)
            return;

        Room room = roomUnit.getRoom();

        if (room == null) {
            return;
        }

        // makes a temporary effect

        roomUnit.getRoom().unIdle(roomUnit.getRoom().getHabbo(roomUnit));
        room.sendComposer(new RoomUserEffectComposer(roomUnit, 4).compose());
        Emulator.getThreading().run(new SendRoomUnitEffectComposer(room, roomUnit), WiredHandler.TELEPORT_DELAY + 1000);

        if (tile == roomUnit.getCurrentLocation()) {
            return;
        }

        if (tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
            RoomTile alternativeTile = null;
            List<RoomTile> optionalTiles = room.getLayout().getTilesAround(tile);

            Collections.reverse(optionalTiles);
            for (RoomTile optionalTile : optionalTiles) {
                if (optionalTile.state != RoomTileState.INVALID && optionalTile.state != RoomTileState.BLOCKED) {
                    alternativeTile = optionalTile;
                    break;
                }
            }

            if (alternativeTile != null) {
                tile = alternativeTile;
            }
        }

        Emulator.getThreading().run(() -> {
            roomUnit.isWiredTeleporting = true;
        }, Math.max(0, WiredHandler.TELEPORT_DELAY - 500));
        Emulator.getThreading()
                .run(new RoomUnitTeleport(roomUnit, room, tile.x, tile.y,
                        tile.getStackHeight() + (tile.state == RoomTileState.SIT ? -0.5 : 0), roomUnit.getEffectId()),
                        WiredHandler.TELEPORT_DELAY);
    }
}
