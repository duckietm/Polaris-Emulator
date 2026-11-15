package com.srth.plugin.websocket.communication.incoming.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;
import java.util.List;

/**
 * Clase que escucha eventos genéricos desde el cliente WebSocket.
 * Maneja una estructura de datos basada en un nombre de evento y un array de
 * parámetros.
 */
public class EventListener extends IncomingMessage {

    public EventListener(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {
        HabboClient clientws = HabboClientManager.getHabboClient(this.client);

        if (clientws == null) {
            return;
        }

        // Establecer peticiones de eventos genéricos para el cliente WebSocket
        RoleplayWebsocketsRoomsInfo.getCraffteoManager().incomingEvents(this.data, clientws);

    }
}