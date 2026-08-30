package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class EventCommand extends Command {
    /**
     * How long the client keeps the announcement on screen before closing it on its own.
     */
    static final int AUTO_CLOSE_SECONDS = 120;

    static final String OPENING_KEY = "hotel.event";
    static final String CLOSING_KEY = "hotel.event.ended";

    private static final String CLOSING_ARGUMENT = "off";
    private static final String CLOSING_ARGUMENT_ALIAS = "stop";

    public EventCommand() {
        super(
                "cmd_event",
                Emulator.getTexts().getValue("commands.keys.cmd_event").split(";"));
    }

    /**
     * Joins everything typed after the command itself into the announced message.
     */
    static String joinMessage(String[] params) {
        StringBuilder message = new StringBuilder();

        for (int i = 1; i < params.length; i++) {
            if (message.length() > 0) {
                message.append(" ");
            }

            message.append(params[i]);
        }

        return message.toString().trim();
    }

    /**
     * A lone "off" (or "stop") closes the event instead of announcing one.
     */
    static boolean isClosingArgument(String[] params) {
        if (params.length != 2) {
            return false;
        }

        String argument = params[1].trim();

        return argument.equalsIgnoreCase(CLOSING_ARGUMENT) || argument.equalsIgnoreCase(CLOSING_ARGUMENT_ALIAS);
    }

    /**
     * Builds the placeholders the client interpolates into the notification texts. The lower case
     * entries are read by the client itself: "linkUrl" and "linkTitle" become the button that walks
     * the user to the room, and "timeout" is the delay after which the box closes on its own.
     *
     * <p>The link is written without the "event:" prefix the official client uses: the link
     * trackers register bare prefixes such as "navigator/", and a prefixed link matches none of
     * them.
     */
    static Map<String, String> notificationKeys(
            String roomName, int roomId, String username, String look, String time, String message, boolean opening) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("ROOMNAME", roomName);
        keys.put("ROOMID", Integer.toString(roomId));
        keys.put("USERNAME", username);
        keys.put("LOOK", look);
        keys.put("TIME", time);
        keys.put("MESSAGE", message);
        keys.put("timeout", Integer.toString(AUTO_CLOSE_SECONDS));

        if (opening) {
            keys.put("linkUrl", "navigator/goto/" + roomId);
            keys.put("linkTitle", "notification.hotel.event.linkTitle");
        }

        return keys;
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Habbo sender = gameClient.getHabbo();
        Room room = sender.getHabboInfo().getCurrentRoom();

        if (room == null) {
            // A whisper only reaches a user standing in a room, so this branch has to
            // use an alert to be seen at all.
            sender.alertLocalized("commands.error.cmd_event.noroom");
            return true;
        }

        boolean closing = isClosingArgument(params);
        String message = closing ? "" : joinMessage(params);

        if (!closing && message.isEmpty()) {
            sender.whisperLocalized("commands.error.cmd_event.usage", RoomChatMessageBubbles.ALERT);
            return true;
        }

        Map<String, String> keys = notificationKeys(
                room.getName(),
                room.getId(),
                sender.getHabboInfo().getUsername(),
                sender.getHabboInfo().getLook(),
                new SimpleDateFormat("HH:mm").format(Emulator.getDate()),
                message,
                !closing);

        ServerMessage notification = new BubbleAlertComposer(closing ? CLOSING_KEY : OPENING_KEY, keys).compose();

        for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .entrySet()) {
            Habbo habbo = set.getValue();

            if (habbo.getHabboStats().blockStaffAlerts) {
                continue;
            }

            habbo.getClient().sendResponse(notification);
        }

        sender.whisperLocalized(
                closing ? "commands.error.cmd_event.ended" : "commands.error.cmd_event.started",
                "%room%",
                room.getName(),
                RoomChatMessageBubbles.ALERT);

        return true;
    }
}
