package polaris.hotel.commands;

import java.util.Locale;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.messages.outgoing.rooms.SpecialRoomEventComposer;

/**
 * The room-wide effects the client knows how to play: a shake, a zoom out, a disco light cycle. They
 * belong to whoever is running the room, so the command is the room's, not a furniture's.
 */
public class RoomSpecialEffectCommand extends Command {
    public RoomSpecialEffectCommand() {
        super(
                "cmd_roomfx",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_roomfx", "roomfx")
                        .split(";"));
    }

    /** The effect names the client answers to, in the order its own switch reads them. */
    private static int effectOf(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "rotate" -> SpecialRoomEventComposer.EFFECT_ROTATE;
            case "shake" -> SpecialRoomEventComposer.EFFECT_SHAKE;
            case "zoom" -> SpecialRoomEventComposer.EFFECT_ZOOM;
            case "disco" -> SpecialRoomEventComposer.EFFECT_DISCO;
            default -> -1;
        };
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return true;

        int effectId = params.length < 2 ? -1 : effectOf(params[1]);

        if (effectId < 0) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue(
                                            "commands.error.cmd_roomfx.unknown",
                                            "Effects: rotate, shake, zoom, disco."),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        room.sendComposer(new SpecialRoomEventComposer(effectId).compose());
        return true;
    }
}
