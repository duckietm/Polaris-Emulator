package com.srth.plugin.ActionItems.Actions.Pescar;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.Rooms.Cycleables.RoomPesca;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.ItemUsuario;

import java.util.Random;

public class Pescar extends Scheduler {

    private HabboItem item;
    private Habbo habbo;
    private int roomUnitId = -1;
    private int elapsedSeconds = 0;
    private int default_sumatoria = 10;

    private int exito, cant_peces_aleatorio;

    public Pescar(HabboItem item, Habbo habbo) {
        super(1);

        this.item = item;
        this.habbo = habbo;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        try {
            if (habbo.getRoomUnit().getRoomUnitType() == RoomUnitType.USER) {

                if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
                    return;
                }

                if (habbo != null) {
                    room.giveEffect(habbo, Integer.valueOf(4), 9);
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    String[] mensaje_process = RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.pescar.process")
                            .split(";");

                    // Ocultar tiempo de proceso y mostrar el nuevo mensaje de pesca
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/pescar/cana_basica.png?v2", mensaje_process[0],
                            3, true);
                    habboRp.setProgressPendiente(true);

                    room.makeSit(habbo);

                    this.roomUnitId = habbo.getRoomUnit().getId();
                    this.elapsedSeconds = 0;
                    this.disposed = false;

                    // Usamos el método run de ThreadPooling con 1000 ms (1 segundo) de retraso
                    Emulator.getThreading().run(this, 1000);

                    int cuenta_aleatorio = Emulator.getRandom().nextInt(100);
                    if (cuenta_aleatorio >= 30) {
                        exito = 1;
                    } else {
                        exito = 0;
                    }

                    cant_peces_aleatorio = Emulator.getRandom().nextInt(6);
                }
            }
        } catch (Exception e) {
        }
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);
        }
    }

    @Override
    public void run() {
        super.run();

        if (this.disposed || roomUnitId == -1 || habbo == null) {
            return;
        }

        try {
            final Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room == null) {
                this.setDisposed(true);
                return;
            }

            RoleplayWebsocketsRoomsInfo.utilities()
                    .LOGGER("Pescar: roomUnitId: " + this.roomUnitId + ", elapsedSeconds: " + this.elapsedSeconds);

            Habbo currentHabbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (currentHabbo != null && currentHabbo.isOnline()) {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(currentHabbo);

                short x = (short) (item.getX() + 2);
                short y = (short) (item.getY() + 0);

                // Validación de distancia o escape de peces
                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(x, y),
                        currentHabbo.getRoomUnit().getCurrentLocation()) || RoomPesca.getEstadoPeces() == 1) {

                    if (RoomPesca.getEstadoPeces() == 1) {
                        currentHabbo.shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("pescar.cana.escape_peces"));
                    }

                    this.resetTime();
                    this.roomUnitId = -1;
                    this.setDisposed(true);

                    room.giveEffect(currentHabbo, Integer.valueOf(0), -1);

                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.setProgressPendiente(false);

                    ItemActionManager.deletePescar(currentHabbo);
                    return;
                }

                // Incrementamos los segundos transcurridos
                elapsedSeconds++;

                // Evento a los 5 segundos
                if (elapsedSeconds == 5) {
                    room.giveEffect(currentHabbo, Integer.valueOf(4), 10);

                    // Ocultar tiempo de proceso y mostrar el nuevo mensaje de pesca
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    String[] mensaje_process = RoleplayWebsocketsRoomsInfo.getTexts()
                            .getValue("action.pescar.process").split(";");
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/pescar/cana_basica.png?v2", mensaje_process[1],
                            3, true);

                    RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                            "RoleplayWebsocketsRoomsInfo/pescar/cana_basica.png",
                            "action_encarceled.notif.accion.progress", currentHabbo.getClient());
                }

                // FIN DE LA PESCA (10 segundos)
                if (elapsedSeconds >= default_sumatoria) {
                    room.giveEffect(currentHabbo, Integer.valueOf(0), -1);

                    this.resetTime();

                    // Ocultar tiempo de proceso y mostrar el nuevo mensaje de pesca
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    String[] mensaje_process = RoleplayWebsocketsRoomsInfo.getTexts()
                            .getValue("action.pescar.process").split(";");
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/pescar/cana_basica.png?v2", mensaje_process[2],
                            3, true);
                    currentHabbo.talk("*" + mensaje_process[2] + "*");

                    Emulator.getThreading().run(() -> {
                        try {
                            final HabboRp habboRpSet = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                    .getHabboRp(currentHabbo);

                            if (exito == 1) {
                                String[] mensaje_action_data = RoleplayWebsocketsRoomsInfo.getTexts()
                                        .getValue("action.pescar.action_success").split(";");
                                int aleatorio = Emulator.getRandom().nextInt(mensaje_action_data.length);

                                if (cant_peces_aleatorio == 0) {
                                    cant_peces_aleatorio = 1;
                                }

                                int uso_cana_basica = habboRpSet.checkSettings("uses_cana_basica", 15);
                                int uso_cana_intermedia = habboRpSet.checkSettings("uses_cana_intermedia", 10);

                                if (habboRpSet.getHanditem() == 2000) {
                                    Random random = new Random();
                                    cant_peces_aleatorio = random.nextInt(5 - 3 + 1) + 5;

                                    int operacion = uso_cana_intermedia - 1;
                                    habboRpSet.updateData("uses_cana_intermedia", operacion + "");

                                    currentHabbo.shout(mensaje_action_data[aleatorio].replace("%cantidad%",
                                            cant_peces_aleatorio + ""), RoomChatMessageBubbles.GREEN);
                                    habboRpSet.getNewInventoryHabbo().addCantidadItem("pez", cant_peces_aleatorio);

                                    if (operacion <= 0) {
                                        habboRpSet.showMessage("/RoleplayWebsocketsRoomsInfo/pescar/cana_basica.png",
                                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                        .getValue("pescar.cana.break")
                                                        .replace("%TYPE%", "intermedia"));
                                        currentHabbo.whisper(
                                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("pescar.cana.break")
                                                        .replace("%TYPE%", "intermedia"));

                                        habboRpSet.setHanditem(0);
                                        room.giveHandItem(currentHabbo.getRoomUnit(), 0);
                                        habboRpSet.updateData("uses_cana_intermedia", 5 + "");

                                        ItemUsuario invItem = habboRpSet.getNewInventoryHabbo()
                                                .getItem("cana_intermedia");
                                        if (invItem != null) {
                                            habboRpSet.getNewInventoryHabbo().remove(invItem.getItemId());
                                        }
                                    }

                                } else if (habboRpSet.getHanditem() == 1092) {
                                    int operacion = uso_cana_basica - 1;
                                    habboRpSet.updateData("uses_cana_basica", operacion + "");

                                    currentHabbo.shout(mensaje_action_data[aleatorio].replace("%cantidad%",
                                            cant_peces_aleatorio + ""), RoomChatMessageBubbles.GREEN);
                                    habboRpSet.getNewInventoryHabbo().addCantidadItem("pez", cant_peces_aleatorio);

                                    if (operacion <= 0) {
                                        habboRpSet.showMessage("/RoleplayWebsocketsRoomsInfo/pescar/cana_basica.png",
                                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                        .getValue("pescar.cana.break")
                                                        .replace("%TYPE%", "basica"));
                                        currentHabbo.whisper(
                                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("pescar.cana.break")
                                                        .replace("%TYPE%", "basica"));

                                        habboRpSet.setHanditem(0);
                                        room.giveHandItem(currentHabbo.getRoomUnit(), 0);

                                        ItemUsuario invItem = habboRpSet.getNewInventoryHabbo().getItem("cana_basica");
                                        habboRpSet.updateData("uses_cana_basica", 10 + "");
                                        if (invItem != null) {
                                            habboRpSet.getNewInventoryHabbo().remove(invItem.getItemId());
                                        }
                                    }
                                }

                            } else {
                                String[] mensaje_action_data = RoleplayWebsocketsRoomsInfo.getTexts()
                                        .getValue("action.pescar.action_notsuccess").split(";");
                                int aleatorio = Emulator.getRandom().nextInt(mensaje_action_data.length);
                                currentHabbo.shout(mensaje_action_data[aleatorio], RoomChatMessageBubbles.RED);
                            }

                            habboRpSet.getVidaHambreDormir().ocultarTimeProcess();
                            habboRpSet.setProgressPendiente(false);

                            this.roomUnitId = -1;
                            this.setDisposed(true);

                            ItemActionManager.deletePescar(currentHabbo);

                        } catch (Exception e) {
                        }
                    }, 1000);

                    return; // Terminó la pesca, no reprogramamos más
                }

                // REPROGRAMAR EL SIGUIENTE SEGUNDO:
                // Como ThreadPooling.run(run, delay) ejecuta solo una vez tras el delay,
                // volvemos a lanzarlo nosotros mismos aquí para que corra cada 1 segundo
                // exacto.
                if (!this.disposed) {
                    Emulator.getThreading().run(this, 1000);
                }
            } else {
                this.setDisposed(true);
            }
        } catch (Exception e) {
            this.setDisposed(true);
        }
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }

    private void resetTime() {
        this.habbo = null;
        this.roomUnitId = -1;
        this.elapsedSeconds = 0;
    }
}