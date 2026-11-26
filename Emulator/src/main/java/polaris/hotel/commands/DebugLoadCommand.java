package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.bots.DebugLoadBot;
import polaris.hotel.bots.DebugLoadFigures;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.messages.outgoing.rooms.users.RoomUsersComposer;

/**
 * {@code :debugload [amount | clear]} fills the room with walking, talking bots that all look different, to see
 * how a client holds up in a crowd. They come in through the door a few at a time and exist in memory only
 * ({@link DebugLoadBot}): {@code :debugload clear}, or the room unloading, takes them away again.
 *
 * <p>Two locks: the permission (the top rank alone by default) and the emulator setting {@code debug_load},
 * which is off unless a hotel turns it on.
 */
public class DebugLoadCommand extends Command {
    public static final String SETTING = "debug_load";
    static final int DEFAULT_AMOUNT = 500;
    static final int MAX_AMOUNT = 2000;
    /** Bots that walk in together, and the pause before the next group. */
    static final int GROUP_SIZE = 10;

    static final int GROUP_PAUSE_MS = 250;
    static final short CHAT_DELAY_SECONDS = 5;
    static final String[] CHAT_LINES = {
        "Nog meer test",
        "dit is een test",
        "Wat een test",
        "Ehhh test 1 2 3",
        "Welke test???????",
        "Ik wil wel testen !!!!",
        "hoeveel !",
        "ah oke een test",
        "Ja"
    };

    public DebugLoadCommand() {
        super(
                "cmd_debug_load",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_debug_load", "debugload")
                        .split(";"));
    }

    /** The amount asked for: the default without one, never more than the cap, zero for anything else. */
    static int amountOf(String[] params) {
        if (params.length < 2) return DEFAULT_AMOUNT;

        try {
            int amount = Integer.parseInt(params[1]);
            return amount <= 0 ? 0 : Math.min(amount, MAX_AMOUNT);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User user = gameClient.getUser();
        Room room = user.getUserInfo().getCurrentRoom();

        if (!Emulator.getConfig().getBoolean(SETTING, false)) {
            user.whisper(text("commands.error.cmd_debug_load.disabled"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (room == null || room.getLayout() == null || room.getLayout().getDoorTile() == null) {
            user.whisper(text("commands.error.cmd_debug_load.no_room"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length >= 2 && params[1].equalsIgnoreCase("clear")) {
            int removed = clear(room);
            user.whisper(
                    text("commands.success.cmd_debug_load.cleared").replace("%amount%", String.valueOf(removed)),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        int amount = amountOf(params);
        if (amount == 0) {
            user.whisper(text("commands.error.cmd_debug_load.usage"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        List<DebugLoadFigures.Look> looks = DebugLoadFigures.different(amount, ThreadLocalRandom.current());
        int firstId = DebugLoadBot.FIRST_ID - loadBots(room).size();
        String ownerName = user.getUserInfo().getUsername();
        int ownerId = user.getUserInfo().getId();

        for (int start = 0; start < looks.size(); start += GROUP_SIZE) {
            List<DebugLoadFigures.Look> group = looks.subList(start, Math.min(looks.size(), start + GROUP_SIZE));
            int groupFirstId = firstId - start;
            Emulator.getThreading()
                    .run(
                            () -> enter(room, group, groupFirstId, ownerId, ownerName),
                            (long) (start / GROUP_SIZE) * GROUP_PAUSE_MS);
        }

        user.whisper(
                text("commands.success.cmd_debug_load").replace("%amount%", String.valueOf(looks.size())),
                RoomChatMessageBubbles.ALERT);
        return true;
    }

    private static String text(String key) {
        return Emulator.getTexts().getValue(key);
    }

    private static List<Bot> loadBots(Room room) {
        List<Bot> bots = new ArrayList<>();
        synchronized (room.getCurrentBots()) {
            for (Bot bot : room.getCurrentBots().values()) {
                if (bot instanceof DebugLoadBot) bots.add(bot);
            }
        }
        return bots;
    }

    private static int clear(Room room) {
        List<Bot> bots = loadBots(room);
        for (Bot bot : bots) room.removeBot(bot);
        return bots.size();
    }

    /** One group walks in: each bot appears on the door tile and heads for a tile of its own. */
    private static void enter(
            Room room, List<DebugLoadFigures.Look> group, int firstId, int ownerId, String ownerName) {
        // The room was left and unloaded while the test was still coming in.
        if (!room.isLoaded() || room.getLayout() == null) return;

        RoomTile door = room.getLayout().getDoorTile();
        if (door == null) return;

        double height = room.getTopHeightAt(door.x, door.y);

        for (int index = 0; index < group.size(); index++) {
            DebugLoadFigures.Look look = group.get(index);
            int id = firstId - index;
            DebugLoadBot bot = new DebugLoadBot(
                    id,
                    "Test" + (DebugLoadBot.FIRST_ID - id + 1),
                    "Load test",
                    look.figure(),
                    look.male() ? UserGender.M : UserGender.F,
                    ownerId,
                    ownerName);
            bot.getChatLines().addAll(List.of(CHAT_LINES));
            // Not on the room's tick (chatAuto), where a crowd speaks in bursts: each on a clock of its own.
            bot.setChatAuto(false);
            bot.setChatDelay(CHAT_DELAY_SECONDS);
            bot.setCanWalk(true);

            RoomUnit unit = new RoomUnit();
            unit.setRotation(RoomUserRotation.values()[room.getLayout().getDoorDirection() % 8]);
            unit.setLocation(door);
            unit.setPreviousLocationZ(height);
            unit.setZ(height);
            unit.setPathFinderRoom(room);
            unit.setRoomUnitType(RoomUnitType.BOT);
            unit.setCanWalk(true);

            // A tile of its own to walk to and to wander around, so the crowd spreads through the room.
            RoomTile home = room.getRandomWalkableTile();
            unit.setBotStartLocation(home == null ? door : home);

            bot.setRoomUnit(unit);
            bot.setRoom(room);
            room.addBot(bot);
            room.sendComposer(new RoomUsersComposer(bot).compose());
            room.sendComposer(new RoomUserStatusComposer(unit).compose());

            if (home != null) unit.setGoalLocation(home);
            bot.speakOnItsOwnClock(CHAT_LINES, CHAT_DELAY_SECONDS * 1000);
        }
    }
}
