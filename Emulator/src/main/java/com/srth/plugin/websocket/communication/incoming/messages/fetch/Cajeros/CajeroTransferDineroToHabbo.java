package com.srth.plugin.websocket.communication.incoming.messages.fetch.Cajeros;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.ActionItems.Actions.Cajeros.hack_cajero.OpenCajero;
import com.srth.plugin.Crafteos.CrafteosTypes;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;

public class CajeroTransferDineroToHabbo extends IncomingMessage {

    public CajeroTransferDineroToHabbo(String header, THashMap<String, Object> data, Channel client) {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() {
        try {
            HabboClient clientws = HabboClientManager.getHabboClient(this.client);
            if (clientws == null) {
                return;
            }
            
            Habbo habbo = clientws.getHabbo();

            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("LLEGANDO AQUI");

            if (habbo == null)
                return;

            // 2. Extraer datos del JSON enviado por el cliente
            String username = (String) this.data.get("username");

            // Cambiamos esto para que soporte Double/Number sin que explote
            Number montoObj = (Number) this.data.get("monto");
            int monto = montoObj != null ? montoObj.intValue() : 0;

            Habbo habboCuestion = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);

            if (habboCuestion == null) {
                habbo.alert("El usuario no se encuentra o no esta conectado.");
                return;
            } else {

                if (habboCuestion == habbo) {
                    habbo.alert("No puedes transferirte dinero a ti mismo.");
                    return;
                }

                try {

                    HabboRp habboRpMy = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                            .getHabboRp(habbo);
                    HabboRp habboRpOther = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                            .getHabboRp(habboCuestion);

                    int dinero_transferencia = monto;
                    int dinero_depositado_en_cuenta = habboRpMy.getDineroDeposited();

                    if (dinero_transferencia > dinero_depositado_en_cuenta) {
                        habbo.alert("La cantidad a transferir supera tu monto actual.");
                        return;
                    } else {
                        int operacion = habboRpMy.getDineroDeposited() - dinero_transferencia;
                        habboRpMy.setDineroDeposited(operacion);

                        int sumatoriaOtro = habboRpOther.getDineroDeposited() + dinero_transferencia;
                        habboRpOther.setDineroDeposited(sumatoriaOtro);

                        habbo.alert("Dinero transferido a: " + habboCuestion.getHabboInfo().getUsername()
                                + " exitosamente");

                        ItemActionManager.getCajeroAbierto(habbo).showVentana(habbo);

                        if (ItemActionManager.getCajeroAbierto(habboCuestion) != null) {
                            habboCuestion.alert(
                                    "Recibiste: " + dinero_transferencia + " exitosamente, por parte de: "
                                            + habbo.getHabboInfo().getUsername());
                            ItemActionManager.getCajeroAbierto(habboCuestion).showVentana(habboCuestion);
                        }

                        return;
                    }
                } catch (Exception e) {

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}