package polaris.hotel.rooms;

import java.util.ArrayList;
import java.util.Collection;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.ServerMessageFrame;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;

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

        for (User user : this.room.getUsers()) {
            if (user.getClient() == null) {
                continue;
            }

            user.getClient().sendResponse(message);
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

        for (User user : this.room.getUsers()) {
            if (user.getClient() == null) {
                continue;
            }

            user.getClient().sendResponses(responses);
        }
    }

    /**
     * Sends a message to all Habbos with rights in the room.
     */
    public void sendComposerToUsersWithRights(ServerMessage message) {
        prepareBroadcast(message);

        for (User user : this.room.getUsers()) {
            if (this.room.hasRights(user)) {
                user.getClient().sendResponse(message);
            }
        }
    }

    // ==================== PET AND BOT CHAT ====================

    /**
     * Sends a pet chat message to all Habbos who don't ignore pets.
     */
    public void petChat(ServerMessage message) {
        prepareBroadcast(message);

        for (User user : this.room.getUsers()) {
            if (!user.getUserStats().ignorePets) {
                user.getClient().sendResponse(message);
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

        for (User user : this.room.getUsers()) {
            if (user == null) {
                continue;
            }
            if (!user.getUserStats().ignoreBots) {
                user.getClient().sendResponse(message);
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
