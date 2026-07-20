package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.ServerMessageFrame;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Manages all messaging and communication within a room.
 * Handles sending messages to Habbos, pet/bot chat, and alerts.
 */
public class RoomMessagingManager {
    private final Room room;

    public RoomMessagingManager(Room room) {
        this.room = room;
    }

    // ==================== SEND MESSAGES ====================

    /**
     * Sends a message to all Habbos in the room.
     */
    public void sendComposer(ServerMessage message) {
        prepareBroadcast(message);

        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo.getClient() == null) {
                continue;
            }

            habbo.getClient().sendResponse(message);
        }
    }

    public void sendComposers(Collection<ServerMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        ArrayList<ServerMessage> responses = new ArrayList<>();

        for (ServerMessage message : messages) {
            if (message == null) {
                continue;
            }

            responses.add(message);
            prepareBroadcast(message);
        }

        if (responses.isEmpty()) {
            return;
        }

        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo.getClient() == null) {
                continue;
            }

            habbo.getClient().sendResponses(responses);
        }
    }

    /**
     * Sends a message to all Habbos with rights in the room.
     */
    public void sendComposerToHabbosWithRights(ServerMessage message) {
        prepareBroadcast(message);

        for (Habbo habbo : this.room.getHabbos()) {
            if (this.room.hasRights(habbo)) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    // ==================== PET AND BOT CHAT ====================

    /**
     * Sends a pet chat message to all Habbos who don't ignore pets.
     */
    public void petChat(ServerMessage message) {
        prepareBroadcast(message);

        for (Habbo habbo : this.room.getHabbos()) {
            if (!habbo.getHabboStats().ignorePets) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    /**
     * Sends a bot chat message to all Habbos who don't ignore bots.
     */
    public void botChat(ServerMessage message) {
        if (message == null) {
            return;
        }

        prepareBroadcast(message);

        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo == null) {
                continue;
            }
            if (!habbo.getHabboStats().ignoreBots) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    // ==================== ALERTS ====================

    /**
     * Sends an alert message to all Habbos in the room.
     */
    public void alert(String message) {
        this.sendComposer(new GenericAlertComposer(message).compose());
    }

    private static void prepareBroadcast(ServerMessage message) {
        if (message != null) {
            ServerMessageFrame.prepareBroadcast(message);
        }
    }
}
