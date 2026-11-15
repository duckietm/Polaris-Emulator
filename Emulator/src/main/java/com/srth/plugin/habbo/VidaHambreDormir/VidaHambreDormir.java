package com.srth.plugin.habbo.VidaHambreDormir;

import java.sql.SQLException;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Consola.TypeMessageConsola;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.HabboRpManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.outgoing.messages.*;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

public class VidaHambreDormir {

    private final Habbo habbo;
    private final HabboRp habboRp;
    private HabboClient client;

    private int vida, hambre, dormir, higiene, ignore_hambre, action_dormir_active;
    private int lastVida = -1, lastHambre = -1, lastDormir = -1, lastHigiene = -1, lastLevel = -1;
    private int walkedTilesSinceEnergyLoss;

    public Boolean camillaActive = false;

    private final int cantidad_ignore_hambre = RoleplayWebsocketsRoomsInfo.getConfig()
            .getInt("comer_succes.ignore.hambre.cantidad");

    private boolean duchaActive = false;

    public boolean duchaItemRecibido;
    private boolean toggleSonidoVida = true;

    public VidaHambreDormir(Habbo habbo) throws SQLException {
        this.habbo = habbo;
        this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        this.client = habboRp.getHabboClient();

        this.vida = habboRp.checkSettings("global.vida", 100);
        this.hambre = habboRp.checkSettings("global.hambre", 50);
        this.dormir = habboRp.checkSettings("global.dormir", 50);
        this.higiene = habboRp.checkSettings("global.higiene", 50);
        this.ignore_hambre = habboRp.checkSettings("global.ignore.hambre", cantidad_ignore_hambre);
        this.action_dormir_active = habboRp.checkSettings("global_dormir_active", 0);

        this.duchaItemRecibido = habboRp.checkSettings("ducha_item_recibido", 0) == 1;
    }

    public boolean getDuchaActive() {
        return this.duchaActive;
    }

    public void setDuchaActive(boolean value) {
        this.duchaActive = value;
    }

    // --- OPTIMIZACIÓN WEBSOCKET ---
    private boolean statsChanged() {
        int currentLevel = (habboRp.getLevelHabbo() != null) ? habboRp.getLevelHabbo().getLevel() : 0;
        return vida != lastVida || hambre != lastHambre || dormir != lastDormir || higiene != lastHigiene
                || currentLevel != lastLevel;
    }

    private void updateSnapshot() {
        this.lastVida = vida;
        this.lastHambre = hambre;
        this.lastDormir = dormir;
        this.lastHigiene = higiene;
        this.lastLevel = (habboRp.getLevelHabbo() != null) ? habboRp.getLevelHabbo().getLevel() : 0;
    }

    public void ocultStats() throws SQLException {
        // 1. Si el cliente es nulo, intenta inicializarlo antes de proceder
        if (client == null) {
            this.createClient();
            return;
        }

        // 2. Envía el paquete al WebSocket para ocultar la interfaz en el cliente
        // (React)
        // El parámetro 'false' indica que la visibilidad se desactiva
        // El parámetro 'null' evita enviar datos innecesarios al ocultar
        client.send(new ShowInfoMyHabbo("ShowInfoMyHabbo", false, null));
    }

    public void renderStats() throws SQLException {
        if (client == null) {
            this.createClient();
            if (client == null)
                return;
        }
        if (!statsChanged())
            return;
        client.send(new ShowInfoMyHabbo("ShowInfoMyHabbo", true, this.getArray()));
        updateSnapshot();
    }

    public void gotoHospital(String imagen_notification, String mensaje_key_text) throws SQLException {

        // 1. Validaciones básicas de seguridad
        if (this.habbo == null || this.habbo.getHabboInfo().getCurrentRoom() == null) {
            return;
        }

        // 2. Solo se ejecuta si el usuario está en una sala de categoría Roleplay
        if (this.habbo.getHabboInfo().getCurrentRoom().getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                .getInt("global_room.category.roleplay.id")) {

            // 3. Si ya está en la sala del hospital, no lo volvemos a enviar
            if (this.habbo.getHabboInfo().getCurrentRoom().getId() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.job_medico.id")) {

                String mensaje_total;

                // 4. Lógica de mensaje personalizado para muertes por PVP
                if (habboRp.checkSettings("global_shooter.pvp.active", 0) == 1) {
                    String asesino = habboRp.checkSettings("asesined_by", "");
                    mensaje_total = RoleplayWebsocketsRoomsInfo.getTexts().getValue(mensaje_key_text)
                            .replace("%username%", asesino);

                    // Limpiamos los datos del asesino tras procesar la muerte
                    habboRp.saveToDatabase("0", "asesined");
                    habboRp.saveToDatabase("0", "asesined_by");
                } else {
                    mensaje_total = RoleplayWebsocketsRoomsInfo.getTexts().getValue(mensaje_key_text);
                }

                // 5. Mostrar la alerta de burbuja clásica en el cliente Habbo
                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(imagen_notification, mensaje_key_text,
                        this.habbo.getClient());

                // 6. Teletransportar al usuario a la sala del hospital
                this.habbo.getClient().sendResponse(new ForwardToRoomComposer(
                        RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_medico.id")));

                // 7. Si hay conexión WebSocket, enviar la interfaz visual de muerte (React)
                if (client == null) {
                    return;
                }

                JsonArray array = new JsonArray();
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("title", "Muerto");
                jsonObject.addProperty("description", mensaje_total);
                jsonObject.addProperty("msg_confirm", "Aceptar");
                array.add(jsonObject);

                client.send(new ShowVentDeadth("ShowVentDeadth", true, array));
                client.send(new PlaySound("PlaySound", true, "rip.mp3", habbo));

                return;
            }
        }
    }

    public void showTimeProcess(String imagen, String descripcion, int tiempo_segundos, Boolean active)
            throws SQLException {

        if (client == null) {
            return;
        }

        int enabled = 1;
        String imagen_convert;

        if (active == false) {
            enabled = 0;
        }

        if (imagen.equals("")) {
            imagen_convert = imagen;
        } else {
            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String urlIMG = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                    .replace("%url.hotel.base%", url_hotel_base);
            imagen_convert = urlIMG + imagen;
        }

        client.send(new ShowUserTimeProcess("ShowUserTimeProcess", true, enabled, imagen_convert, descripcion,
                tiempo_segundos));
    }

    public void ocultarTimeProcess() throws SQLException {
        // 1. Verificamos que el cliente de WebSocket no sea nulo para evitar errores
        if (client == null) {
            return;
        }

        // 2. Enviamos el paquete ShowUserTimeProcess indicando que debe ocultarse:
        // - El primer "false" indica que el componente se desactiva en el cliente.
        // - El segundo parámetro "0" (enabled) confirma que no hay proceso activo.
        // - Los strings vacíos y el 0 final limpian los datos previos en el estado de
        // React.
        client.send(new ShowUserTimeProcess("ShowUserTimeProcess", false, 0, "", "", 0));
    }

    public void setDormirActive(boolean value) {
        if (value) {
            int new_value = 1;
            this.action_dormir_active = new_value;
            habboRp.updateData("global_dormir_active", String.valueOf(new_value));
        } else {
            int new_value = 0;
            this.action_dormir_active = new_value;
            habboRp.updateData("global_dormir_active", String.valueOf(new_value));
        }
    }

    public void dormir(int cantidad) throws SQLException {
        // 1. Calculamos el nuevo total de sueño sin exceder el límite de 100
        int sumar_total = this.getDormir() + cantidad;

        if (sumar_total >= 100) {
            sumar_total = 100;
        }

        // 2. Actualizamos la variable local
        this.dormir = sumar_total;

        // 3. Guardamos los datos y desactivamos el estado "durmiendo"
        habboRp.updateData("global.dormir", String.valueOf(sumar_total));
        habboRp.updateData("global_dormir_active", "0");
        this.action_dormir_active = 0;

        // 4. Sincronizamos con el WebSocket para actualizar la interfaz
        renderStats();
    }

    public void recuperarEnergia(int cantidad) throws SQLException {
        this.dormir = Math.min(100, this.dormir + Math.max(0, cantidad));
        habboRp.updateData("global.dormir", String.valueOf(this.dormir));
        renderStats();
    }

    public void registerWalkedTile() throws SQLException {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                .getInt("global_room.category.roleplay.id")
                || habboRp.getConstructorSalas()
                || getDormirActive()
                || camillaActive
                || habboRp.getHabboTutorial() == null
                || !habboRp.getHabboTutorial().getCompleted()) {
            resetWalkTracking();
            return;
        }

        walkedTilesSinceEnergyLoss++;
        int tilesPerPoint = Math.max(1,
                RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.rooms.energia.walk.tiles.per.point"));
        int energyLoss = walkedTilesSinceEnergyLoss / tilesPerPoint;
        if (energyLoss > 0) {
            walkedTilesSinceEnergyLoss %= tilesPerPoint;
            restarDormir(energyLoss);
        }
    }

    private void resetWalkTracking() {
        walkedTilesSinceEnergyLoss = 0;
    }

    // --- MÉTODOS DE HIGIENE ---
    public void sumarHigiene(int num) throws SQLException {
        this.higiene = Math.min(100, this.higiene + num);
        habboRp.updateData("global.higiene", String.valueOf(this.higiene));
        renderStats();
    }

    public void restarHigiene(int num) throws SQLException {
        this.higiene = Math.max(0, this.higiene - num);
        habboRp.updateData("global.higiene", String.valueOf(this.higiene));
        renderStats();
    }

    public void resetVida(int num) throws SQLException {
        // 1. Calculamos la sumatoria de la vida actual más la cantidad recibida
        int sumatoria = this.vida + num;

        // 2. Aplicamos el límite máximo de 100 para no desbordar la estadística
        if (sumatoria >= 100) {
            sumatoria = 100;
        }

        // 3. Actualizamos la base de datos o el sistema de persistencia del HabboRP
        habboRp.update("global.vida", String.valueOf(sumatoria));

        // 4. Actualizamos el atributo local de la clase para que el cambio sea
        // inmediato
        this.vida = sumatoria;

        // 5. Sincronizamos con el WebSocket para actualizar la barra de vida en la
        // interfaz de React
        // Recuerda que gracias a la optimización que agregamos, solo se enviará si el
        // valor cambió.
        renderStats();
    }

    public void sumarVida(int value) throws SQLException {
        // 1. Si ya tiene la vida al máximo, no hacemos nada
        if (this.vida >= 100) {
            // Si tenía el efecto de muerte/herido (efecto 13), se lo quitamos al estar sano
            if (habboRp.getEnable() == 13) {
                habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 0, -1);
                habboRp.setEnable(0);
            }
            return;
        }

        // 2. Condición de Roleplay: Solo recupera vida si está bien alimentado (Hambre
        // >= 90) o si está en la camilla del hospital, simulando que recibe atención
        // médica
        if (this.hambre >= 90) {
            // Calculamos la nueva vida sin pasarnos de 100
            int nuevaVida = Math.min(100, this.vida + value);

            // 3. Si hubo un cambio real, actualizamos
            if (nuevaVida != this.vida) {
                this.vida = nuevaVida;

                // Actualizamos caché y base de datos
                habboRp.update("global.vida", String.valueOf(this.vida));
                habboRp.saveToDatabase(String.valueOf(this.vida), "global.vida");

                // 4. Sincronizamos con la UI de React si los stats cambiaron
                renderStats();
            }
        }
    }

    public void sumarVidaProgresiva(int value) throws SQLException {
        // 1. Si ya tiene la vida al máximo, no hacemos nada
        if (this.vida >= 100) {
            // Si tenía el efecto de muerte/herido (efecto 13), se lo quitamos al estar sano
            if (habboRp.getEnable() == 13) {
                habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 0, -1);
                habboRp.setEnable(0);
            }
            return;
        }

        // 2. Condición de Roleplay: Solo recupera vida si está bien alimentado (Hambre
        // >= 90) o si está en la camilla del hospital, simulando que recibe atención
        // médica
        if (this.camillaActive == true) {
            // Calculamos la nueva vida sin pasarnos de 100
            int nuevaVida = Math.min(100, this.vida + value);

            // 3. Si hubo un cambio real, actualizamos
            if (nuevaVida != this.vida) {
                this.vida = nuevaVida;

                // Actualizamos caché y base de datos
                habboRp.update("global.vida", String.valueOf(this.vida));
                habboRp.saveToDatabase(String.valueOf(this.vida), "global.vida");

                // 4. Sincronizamos con la UI de React si los stats cambiaron
                renderStats();
            }
        }
    }

    // --- MÉTODOS DE VIDA ---
    public void restarVida(int num) throws SQLException {
        if (this.habbo == null || habboRp.getDelitosHabbo().getEncarceled())
            return;

        Room room = this.habbo.getHabboInfo().getCurrentRoom();
        if (room == null || room.getId() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_medico.id"))
            return;

        int operacion = this.getVida() - num;
        if (operacion < 0)
            operacion = 0;

        habboRp.updateData("global.vida", operacion + "");
        this.vida = operacion;

        if (this.vida <= 0) {
            manejarMuerte();
        } else if (this.vida <= 60 && client != null) {
            // Lógica: Solo suena si toggleSonidoVida es true
            if (toggleSonidoVida) {
                client.send(new PlaySound("PlaySound", true, "vida_down.mp3", habbo));
            }
            // Invertimos el valor para la siguiente ejecución (true -> false / false ->
            // true)
            toggleSonidoVida = !toggleSonidoVida;
        } else {
            // Opcional: Si la vida sube de 60, reseteamos el toggle para que
            // la próxima vez que baje, empiece sonando.
            toggleSonidoVida = true;
        }

        renderStats();
    }

    private void manejarMuerte() throws SQLException {
        this.acostarHabbo();
        this.habbo.getHabboInfo().getCurrentRoom().giveEffect(this.habbo, 13, -1);
        this.allowMove(false);
        if (habboRp.getCuracionPendiente())
            return;

        if (client != null) {
            JsonArray array = new JsonArray();
            JsonObject jo = new JsonObject();
            jo.addProperty("title", "Muerto");
            jo.addProperty("description",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_alert.hospital.true.vida.empty_new"));
            jo.addProperty("msg_confirm", "Aceptar");
            array.add(jo);
            client.send(new ShowVentDeadth("ShowVentDeadth", true, array));
            client.send(new PlaySound("PlaySound", true, "rip.mp3", habbo));

        }

        habboRp.setCuracionPendiente(true);
        Emulator.getThreading().run(() -> {
            try {
                habboRp.getTareas().insertProgress("Morir");
                if (client != null)
                    client.send(new ShowConfirmAlert("ShowConfirmAlert", "GotoHospital", 1, 1800, true, ""));

                // ENVIAR ALERTA A TODOS
                RoleplayWebsocketsRoomsInfo.getConsolaManager()
                        .sendMessageConsolaGlobal(
                                TypeMessageConsola
                                        .getFormattedMessage(
                                                TypeMessageConsola.DEFAULT_MSG,
                                                habbo,
                                                "Se ha ido al más allá... "));

                habboRp.getVidaHambreDormir().matarHabbo();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 6000);
    }

    // --- MÉTODOS DE SUEÑO (INCLUYE AVISEACTIONDORMIR) ---
    public void restarDormir(int num) throws SQLException {
        if (this.dormir <= 0) {
            comprobateDormir();
            return;
        }
        this.dormir = Math.max(0, this.dormir - num);
        habboRp.updateData("global.dormir", String.valueOf(this.dormir));
        if (this.dormir <= 0)
            comprobateDormir();
        renderStats();
    }

    public void comprobateDormir() throws SQLException {
        if (this.dormir > 1 || this.vida <= 1)
            return;
        Room room = this.habbo.getHabboInfo().getCurrentRoom();
        if (room != null)
            this.aviseActionDormir(room);
    }

    public void aviseActionDormir(Room room) throws SQLException {
        if (this.habbo == null || habboRp.getProgressPendiente())
            return;

        this.acostarHabbo();
        this.allowMove(false);
        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/dormir/suenos/dormir2.png",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_alert.dormir.empty"));

        if (client != null)
            client.send(new RemoveChatClient("RemoveChatClient", true));

        Emulator.getThreading().run(() -> {
            this.habbo.getRoomUnit().cmdLay = false;
            if (this.habbo.getHabboInfo().getCurrentRoom() != null)
                this.habbo.getHabboInfo().getCurrentRoom().updateHabbo(this.habbo);
            this.allowMove(true);
            if (this.habbo.getHabboInfo().getCurrentRoom() != null)
                this.habbo.getHabboInfo().getCurrentRoom().unIdle(habbo);
            try {
                if (client != null)
                    client.send(new RemoveChatClient("RemoveChatClient", false));
            } catch (Exception ignored) {
            }
        }, 10000);
    }

    // --- AUXILIARES Y RENDER ---
    public void acostarHabbo() {
        if (this.habbo == null || this.habbo.getRoomUnit() == null)
            return;
        if (habbo.getRoomUnit().isWalking())
            habbo.getRoomUnit().stopWalking();
        this.habbo.getRoomUnit().cmdLay = true;
        this.habbo.getHabboInfo().getCurrentRoom().updateHabbo(this.habbo);
        this.habbo.getRoomUnit().setStatus(RoomUnitStatus.LAY, "0.5");
        this.habbo.getHabboInfo().getCurrentRoom()
                .sendComposer(new RoomUserStatusComposer(this.habbo.getRoomUnit()).compose());
    }

    public void allowMove(Boolean value) {
        if (this.habbo.getRoomUnit() != null)
            this.habbo.getRoomUnit().setCanWalk(value);
    }

    public JsonArray getArray() {
        JsonArray array = new JsonArray();
        JsonObject obj = new JsonObject();
        String lookUrl = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
                + "&headonly=1&size=a&gesture=sml&head_direction=2";
        obj.addProperty("user_id", this.habbo.getHabboInfo().getId());
        obj.addProperty("username", this.habbo.getHabboInfo().getUsername());
        obj.addProperty("look", lookUrl.replace("%LOOK%", habbo.getHabboInfo().getLook()));
        obj.addProperty("vida", this.vida);
        obj.addProperty("hambre", this.hambre);
        obj.addProperty("dormir", this.dormir);
        obj.addProperty("higiene", this.higiene);
        obj.addProperty("level", (habboRp.getLevelHabbo() != null) ? habboRp.getLevelHabbo().getLevel() : 0);
        array.add(obj);
        return array;
    }

    public void createClient() {
        try {
            if (this.client != null || this.habbo == null)
                return;
            this.client = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(this.habbo).getHabboClient();
        } catch (Exception ignored) {
        }
    }

    public void ignoreHambreRest() {
        // Si ya es 0 o menor, nos aseguramos de que se quede en 0 y salimos
        if (this.ignore_hambre <= 0) {
            this.ignore_hambre = 0;
            return;
        }

        // Restamos 1 al contador actual
        this.ignore_hambre--;

        // Guardamos el nuevo valor en la base de datos/settings del Habbo
        habboRp.saveToDatabase(String.valueOf(this.ignore_hambre), "global.ignore.hambre");

        // Nota: No llamamos a renderStats() aquí porque el "ignore_hambre"
        // suele ser una estadística interna que no se muestra en la UI de barras.
    }

    public void ignoreHambre(int sum) {
        // 1. Calculamos la nueva sumatoria sumando los puntos actuales + el extra
        int sumatoria = this.ignore_hambre + sum;

        // 2. Establecemos un límite máximo para que no se acumule saciedad infinita.
        // Se usa la fórmula: cantidad_configurada * 2 + 2
        if (sumatoria >= cantidad_ignore_hambre * 2 + 2) {
            sumatoria = cantidad_ignore_hambre * 2 + 2;
        }

        // 3. Guardamos el valor en la base de datos/ajustes del Habbo
        habboRp.saveToDatabase(String.valueOf(sumatoria), "global.ignore.hambre");

        // 4. Actualizamos la variable en la instancia actual
        this.ignore_hambre = sumatoria;
    }

    public void comer(int cantidad) throws SQLException {
        // 1. Calculamos el nuevo total de hambre sin superar el límite de 100
        int sumar_total = this.getHambre() + cantidad;

        if (sumar_total >= 100) {
            sumar_total = 100;
        }

        // 2. Actualizamos el valor en la memoria (HabboRp) y en la instancia actual
        habboRp.updateData("global.hambre", String.valueOf(sumar_total));
        this.hambre = sumar_total;

        // 3. Activamos la protección de saciedad (para que el timer no le reste hambre
        // pronto)
        this.ignoreHambre(cantidad_ignore_hambre);

        // 4. Sincronizamos con el WebSocket para actualizar las barras en el cliente
        // React
        renderStats();
    }

    public void forceHambre(int num) throws SQLException {
        // 1. Aseguramos que el valor esté dentro del rango 0-100
        int nuevoValor = Math.max(0, Math.min(100, num));

        // 2. Si el valor es el mismo que ya tiene, no hacemos nada (ahorro de recursos)
        if (this.hambre == nuevoValor) {
            return;
        }

        // 3. Actualizamos la variable local y la base de datos
        this.hambre = nuevoValor;
        habboRp.updateData("global.hambre", String.valueOf(this.hambre));

        // 4. Si el cliente de WebSocket está conectado, sincronizamos la UI
        if (client != null) {
            renderStats();
        }

        // 5. Opcional: Si lo dejas en 0 mediante este método,
        // podrías disparar el daño de vida inmediatamente
        if (this.hambre <= 0) {
            this.restarVida(1);
        }
    }

    public void restarHambre(int num) throws SQLException {
        // 1. Si el Habbo no existe o ya tiene el hambre en 0, no hacemos nada
        if (this.habbo == null || this.hambre <= 0) {
            return;
        }

        // 2. Comprobamos si tiene el beneficio de "ignore_hambre" activo (por haber
        // comido)
        if (this.ignore_hambre >= 1) {
            this.ignoreHambreRest(); // Resta 1 al contador de ignorar hambre
            return;
        }

        // 3. Calculamos el nuevo valor de hambre (usando Math.max para que no baje de
        // 0)
        int nuevaHambre = Math.max(0, this.hambre - num);

        // 4. Si el valor no ha cambiado (ej. ya era 0), salimos
        if (nuevaHambre == this.hambre) {
            return;
        }

        // 5. Actualizamos la variable y la base de datos
        this.hambre = nuevaHambre;
        habboRp.updateData("global.hambre", String.valueOf(this.hambre));

        // 6. Lógica de consecuencias
        if (this.hambre <= 0) {
            // Si el hambre llega a 0, empezamos a restar vida (Inanición)
            this.restarVida(1);
        } else {
            // Si aún tiene hambre pero bajó, enviamos el sonido de aviso
            if (client != null) {
                client.send(new PlaySound("PlaySound", true, "hambre.mp3", habbo));
            }
        }

        // 7. Sincronizamos con el WebSocket (solo si hubo cambios reales)
        renderStats();
    }

    public void matarHabbo() {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem("cofre_madera_rpbyth_a1");
        if (baseItem == null) {
            RoleplayWebsocketsRoomsInfo.utilities()
                    .LOGGER_ERROR("El item del cofre no existe en la base de datos: cofre_madera_rpbyth_a1");
            return;
        }

        Boolean generarCofre = this.habboRp.getNewInventoryHabbo().items_usuario.size() > 0 ? true : false;

        if (generarCofre == true) {
            // 1. Crear el ítem en memoria (Asignado al dueño del Habbo que "muere" o al
            // dueño de la sala)
            HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                    habbo.getHabboInfo().getId(),
                    baseItem,
                    0,
                    0,
                    "");

            if (habboItem == null)
                return;

            // 2. Configurar la ubicación actual del Habbo
            habboItem.setX(habbo.getRoomUnit().getCurrentLocation().x);
            habboItem.setY(habbo.getRoomUnit().getCurrentLocation().y);
            habboItem.setZ(habbo.getRoomUnit().getCurrentLocation().z);
            habboItem.setRotation(0);
            habboItem.setRoomId(room.getId());
            // 3. Guardar en DB para obtener el ID real del item (Importante para evitar
            // fantasmas)
            habboItem.run();

            // 4. Colocarlo en la sala físicamente
            room.addHabboItem(habboItem);
            room.updateItem(habboItem);
            room.run();
            room.sendComposer(new AddFloorItemComposer(habboItem, room.getFurniOwnerNames().get(habboItem.getUserId()))
                    .compose());

            // 5. (Opcional) Si quieres que el item se guarde en la DB de la sala
            // inmediatamente
            Emulator.getThreading().run(habboItem);

            this.habboRp.getNewInventoryHabbo().dropInventoryToCofre(habboItem.getId());
        }

        try {
            habboRp.getVidaHambreDormir().forceHambre(20);
            habboRp.getVidaHambreDormir().restarVida(100);
            habboRp.setEnable(13);
            habboRp.getHabbo().getHabboInfo().getCurrentRoom().giveEffect(habbo, 13, -1);

        } catch (Exception e) {
        }

    }

    // Getters
    public int getVida() {
        return vida;
    }

    public int getHambre() {
        return hambre;
    }

    public int getDormir() {
        return dormir;
    }

    public int getHigiene() {
        return higiene;
    }

    public boolean getDormirActive() {
        return action_dormir_active != 0;
    }

}