package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class ArcturusCommand extends Command {
    public ArcturusCommand() {
        super(null, new String[] {"arcturus", "emulator"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            gameClient
                    .getUser()
                    .whisper(
                            "This hotel is powered by Arcturus Emulator! \r"
                                    + "Cet hôtel est alimenté par Arcturus émulateur! \r"
                                    + "Dit hotel draait op Arcturus Emulator! \r"
                                    + "Este hotel está propulsado por Arcturus emulador! \r"
                                    + "Hotellet drivs av Arcturus Emulator! \r"
                                    + "Das Hotel gehört zu Arcturus Emulator betrieben!",
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
