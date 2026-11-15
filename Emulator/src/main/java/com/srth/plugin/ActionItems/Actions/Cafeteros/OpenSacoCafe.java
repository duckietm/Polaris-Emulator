package com.srth.plugin.ActionItems.Actions.Cafeteros;

import java.util.Random;
import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;
import gnu.trove.map.hash.THashMap;

public class OpenSacoCafe extends Scheduler {

    private HabboItem item;
    private Habbo habbo;
    private int roomUnitId = -1;
    private int elapsedSeconds = 0;
    private final int default_sumatoria = 30; // 30 segundos totales

    public static final THashMap<Habbo, OpenSacoCafe> users = new THashMap<>();

    public OpenSacoCafe(HabboItem habboItem, Habbo habbo) {
        super(1); // Se ejecuta cada segundo
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp == null || habboItem == null || habbo == null)
                return;

            this.habbo = habbo;
            this.item = habboItem;
            Room room = habbo.getHabboInfo().getCurrentRoom();

            if (room != null) {
                // Dar efecto visual inicial
                room.giveEffect(habbo, 4, 9);
                room.makeSit(habbo); // Opcional, si quieres que se siente como en la pesca

                // Ocultar tiempo anterior si lo hubiera y mostrar el proceso en pantalla
                habboRp.getVidaHambreDormir().ocultarTimeProcess();
                habboRp.getVidaHambreDormir().showTimeProcess(
                        "RoleplayWebsocketsRoomsInfo/job_cafetero/maquina.png?v2",
                        "Iniciando proceso de molienda...",
                        3, true);
                habboRp.setProgressPendiente(true);

                this.roomUnitId = habbo.getRoomUnit().getId();
                this.elapsedSeconds = 0;
                this.disposed = false;

                // Lanzar scheduler
                Emulator.getThreading().run(this, 1000);
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);

                users.put(habbo, this);
            }
        } catch (Exception ignored) {
        }
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);
        }
    }

    public void ocultVentana(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp != null) {
                habboRp.getHabboClient()
                        .send(new MessageManagerDouble("MessageManager", false, Outcoming.OpenCajero, null, null));
            }
        } catch (Exception ignored) {
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

            Habbo currentHabbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (currentHabbo != null && currentHabbo.isOnline()) {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(currentHabbo);

                // 1. Validación de distancia (adyacente al saco/item)
                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                        currentHabbo.getRoomUnit().getCurrentLocation())) {

                    this.resetTime();
                    this.roomUnitId = -1;
                    this.setDisposed(true);

                    room.giveEffect(currentHabbo, 0, -1);
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.setProgressPendiente(false);

                    this.stopProcess();
                    return;
                }

                // Incrementamos los segundos transcurridos
                elapsedSeconds++;

                // Evento intermedio (por ejemplo, a los 10 y 20 segundos)
                if (elapsedSeconds == 10 || elapsedSeconds == 20) {
                    room.giveEffect(currentHabbo, 4, 10);

                    String[] mensajesGranos = {
                            "¡Huele a gloria! Estos granos son de primera calidad...",
                            "Moliendo con fuerza... el aroma del café recién molido es increíble.",
                            "Ya casi termino, estos granos están quedando perfectos.",
                            "Girando el molinillo... ¡Esto va a estar bien cargado!"
                    };
                    Random rand = new Random();
                    String msgAleatorio = mensajesGranos[rand.nextInt(mensajesGranos.length)];

                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/job_cafetero/maquina.png?v2", msgAleatorio,
                            3, true);

                    currentHabbo.talk(msgAleatorio, RoomChatMessageBubbles.NORMAL);
                }

                // FIN DEL PROCESO (30 segundos)
                if (elapsedSeconds >= default_sumatoria) {
                    room.giveEffect(currentHabbo, 0, -1);
                    this.resetTime();

                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    String mensajeFinal = "¡Listo! He terminado de moler todo. El café está listo para preparar.";
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/job_cafetero/maquina.png?v2", mensajeFinal,
                            3, true);
                    currentHabbo.talk("*" + mensajeFinal + "*", RoomChatMessageBubbles.GREEN);

                    Emulator.getThreading().run(() -> {
                        try {
                            final HabboRp habboRpSet = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                    .getHabboRp(currentHabbo);

                            if (habboRpSet != null) {
                                habboRpSet.getVidaHambreDormir().ocultarTimeProcess();
                                habboRpSet.setProgressPendiente(false);

                                habboRpSet.showMessage("RoleplayWebsocketsRoomsInfo/job_cafetero/maquina.png",
                                        "Acércate a la máquina para preparar el café");

                                new EventosEmpleosTutorial(currentHabbo).eventTomarGranosDeCafe();
                                habboRpSet.registerCache("granos_cafe_progreso", "1");
                            }

                            this.roomUnitId = -1;
                            this.setDisposed(true);
                            this.stopProcess();

                        } catch (Exception e) {
                        }
                    }, 1000);

                    return;
                }

                // Reprogramar el siguiente segundo si no ha terminado
                if (!this.disposed) {
                    Emulator.getThreading().run(this, 1000);
                }
            } else {
                this.setDisposed(true);
                this.stopProcess();
            }
        } catch (Exception e) {
            this.setDisposed(true);
            this.stopProcess();
        }
    }

    private void stopProcess() {
        this.roomUnitId = -1;
        if (habbo != null) {
            users.remove(habbo);
            this.ocultVentana(habbo);
        }
        this.setDisposed(true);
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