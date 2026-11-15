package com.srth.plugin.habbo.Frank.Frank;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Frank.Frank.ActionEvents.CongelarUser;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import gnu.trove.map.hash.THashMap;

public class FrankManager {

        HabboRp habboRp = null;

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                        .replace("%url.hotel.base%", url_hotel_base);

        public FrankManager() {

        }

        public void show(String name_key, Habbo habbo) throws SQLException {

                this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (this.habboRp.getHabboClient() == null) {
                        return;
                }

                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                jsonObject = new JsonObject();
                jsonObject.addProperty("title",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_title"));
                jsonObject.addProperty("mensaje",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_text_inicio"));
                jsonObject.addProperty("buttons_quest",
                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue(name_key + "_ventana_text_buttones_quest"));
                jsonObject.addProperty("button_normal",
                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue(name_key + "_ventana_text_button_normal"));
                jsonObject.addProperty("button_roleplay",
                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue(name_key + "_ventana_text_button_roleplay"));
                jsonObject.addProperty("button_no_show",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_text_no_show"));
                jsonObject.addProperty("text_actual",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_room_actual"));
                jsonObject.addProperty("image_frank",
                                global_url_images + "/frank_hello.gif");
                jsonObject.addProperty("room_type", 1);

                jsonObject.addProperty("room_id_roleplay",
                                RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_medico.id"));
                jsonObject.addProperty("room_id_normal", Emulator.getConfig().getInt("hotel.home.room"));
                jsonObject.addProperty("roleplay_category_id",
                                RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_room.category.roleplay.id"));

                Array.add(jsonObject);

                this.habboRp.getHabboClient()
                                .send(new MessageManager("MessageManager", true, Outcoming.ViewFrankWelcome, Array));
        }

        public void showSelect(String name_key, Habbo habbo) throws SQLException {

                this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (this.habboRp.getHabboClient() == null) {
                        return;
                }

                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                jsonObject = new JsonObject();
                jsonObject.addProperty("title",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_title"));
                jsonObject.addProperty("mensaje",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_text_inicio"));
                jsonObject.addProperty("buttons_quest",
                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue(name_key + "_ventana_text_buttones_quest"));
                jsonObject.addProperty("button_accept",
                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue(name_key + "_ventana_text_button_accept"));
                jsonObject.addProperty("button_decline",
                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue(name_key + "_ventana_text_button_decline"));

                jsonObject.addProperty("button_accept_event",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_accept_event"));
                jsonObject.addProperty("button_decline_event",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_decline_event"));

                jsonObject.addProperty("image_frank",
                                global_url_images + "/frank_hello.gif");
                jsonObject.addProperty("room_id_roleplay",
                                RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_medico.id"));
                jsonObject.addProperty("room_id_normal",
                                RoleplayWebsocketsRoomsInfo.getConfig().getInt("hotel.home.room"));
                jsonObject.addProperty("roleplay_category_id",
                                RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_room.category.roleplay.id"));

                Array.add(jsonObject);

                this.habboRp.getHabboClient()
                                .send(new MessageManager("MessageManager", true, Outcoming.ViewFrankSelect, Array));
        }

        public void ocultSelect(Habbo habbo) throws SQLException {
                this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                if (this.habboRp.getHabboClient() == null) {
                        return;
                }
                this.habboRp.getHabboClient()
                                .send(new MessageManager("MessageManager", false, Outcoming.ViewFrankSelect, null));
        }

        public void IncomingListActions(THashMap<String, Object> data, HabboClient wsclient) {
                try {

                        String name = (String) data.get("name");

                        if (name.equalsIgnoreCase("IniciarTutorial")) {
                                this.ocultSelect(wsclient.getHabbo());
                        }

                        if (name.equalsIgnoreCase("AbortarTutorial")) {
                                this.ocultSelect(wsclient.getHabbo());
                                new CongelarUser().congelar(wsclient.getHabbo());
                                ;
                        }

                        if (name.equalsIgnoreCase("NoShowSelectFrank")) {
                                this.ocultSelect(wsclient.getHabbo());
                        }

                        if (name.equalsIgnoreCase("GoToRoomEventTutorial")) {

                                this.ocultSelect(wsclient.getHabbo());

                                int room_id = (int) ((double) data.get("action"));

                                Room room_visit = Emulator.getGameEnvironment().getRoomManager().loadRoom(room_id);
                                String MSG1 = RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue("window_mapa.msg_visit_exitosa")
                                                .replace("%room_name%", room_visit.getName());
                                wsclient.getHabbo().talk(MSG1, RoomChatMessageBubbles.NORMAL);

                                wsclient.getHabbo().goToRoom(room_id);
                        }

                } catch (Exception e) {

                }
        }

}
