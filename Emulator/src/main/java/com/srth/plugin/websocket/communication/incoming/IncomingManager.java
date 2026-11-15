package com.srth.plugin.websocket.communication.incoming;

import com.srth.plugin.websocket.communication.incoming.messages.*;
import com.srth.plugin.websocket.communication.incoming.messages.fetch.GetCrafteosDataRequest;
import com.srth.plugin.websocket.communication.incoming.messages.fetch.GetJobsWindowsDataRequest;
import com.srth.plugin.websocket.communication.incoming.messages.fetch.GetShopBotsDataRequest;
import com.srth.plugin.websocket.communication.incoming.messages.fetch.GetTurnosJobDataRequest;
import com.srth.plugin.websocket.communication.incoming.messages.fetch.Cajeros.CajeroTransferDineroToHabbo;
import com.srth.plugin.websocket.communication.incoming.messages.inworks.*;

import gnu.trove.map.hash.THashMap;

public class IncomingManager {
    private static IncomingManager instance = null;
    private THashMap<String, Class<? extends IncomingMessage>> messages = new THashMap<>();

    public IncomingManager() {
        this.registerMessages();
    }

    public static IncomingManager getInstance() {
        if (instance == null)
            return instance = new IncomingManager();
        else
            return instance;
    }

    public void registerMessages() {
        this.messages.put("SSOTicketEvent", SSOTicketEvent.class);
        this.messages.put("PingEvent", PingEvent.class);
        this.messages.put("GoToRoomEvent", GoToRoomEvent.class);

        this.messages.put("RequestActionEvent", RequestActionEvent.class);
        this.messages.put("SendConfirmAlert", ReceibedConfirmAlert.class);

        this.messages.put("OpenAboutWorks", OpenAboutWorks.class);
        this.messages.put("GoToRoomWorks", GoToRoomWorks.class);
        this.messages.put("NewUserTaskActive", NewUserTaskActive.class);
        this.messages.put("CancelTaskActive", CancelTaskActive.class);

        this.messages.put("OpenVentSelectConfirm", OpenVentSelectConfirm.class);
        this.messages.put("SendVentSelectConfirm", ReceibedVentSelectConfirm.class);

        this.messages.put("RequestUserActionsEvent", RequestUserActionsEvent.class);
        this.messages.put("RequestItemActionsEvent", RequestItemActionsEvent.class);

        this.messages.put("MessagesManager", MessagesManager.class);

        // FETCH
        this.messages.put("GetTurnosJobDataRequest", GetTurnosJobDataRequest.class);
        this.messages.put("GetJobsDataRequest", GetJobsWindowsDataRequest.class);
        this.messages.put("GetCrafteosDataRequest", GetCrafteosDataRequest.class);
        this.messages.put("GetShopBotsDataRequest", GetShopBotsDataRequest.class);

        // Cajeros
        this.messages.put("CajeroTransferDineroToHabbo", CajeroTransferDineroToHabbo.class);

        this.messages.put("EventListener", EventListener.class);

    }

    public THashMap<String, Class<? extends IncomingMessage>> getMessages() {
        return this.messages;
    }

}
