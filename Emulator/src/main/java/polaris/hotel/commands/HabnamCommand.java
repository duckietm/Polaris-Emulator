package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;

public class HabnamCommand extends Command {
    public HabnamCommand() {
        super(null, new String[] {"habnam", "gangnam"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserStats().hasActiveClub()) {
            if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
                gameClient.getUser().getUserInfo().getCurrentRoom().giveEffect(gameClient.getUser(), 140, 30);
                return true;
            }
        }

        return false;
    }
}
