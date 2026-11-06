package polaris.hotel.games.battlebanzai;

import polaris.hotel.games.Game;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.GameTeam;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.interactions.games.InteractionGameGate;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredFreezeUtil;

public class BattleBanzaiGameTeam extends GameTeam {
    public BattleBanzaiGameTeam(GameTeamColors teamColor) {
        super(teamColor);
    }

    @Override
    public void addMember(GamePlayer gamePlayer) {
        super.addMember(gamePlayer);

        if (!WiredFreezeUtil.isFrozen(gamePlayer.getUser().getRoomUnit())) {
            gamePlayer
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .giveEffect(gamePlayer.getUser(), BattleBanzaiGame.effectId + this.teamColor.type, -1);
        }
    }

    @Override
    public void removeMember(GamePlayer gamePlayer) {
        Game game = gamePlayer
                .getUser()
                .getUserInfo()
                .getCurrentRoom()
                .getGame(gamePlayer.getUser().getUserInfo().getCurrentGame());
        Room room = gamePlayer.getUser().getRoomUnit().getRoom();

        if (!WiredFreezeUtil.isFrozen(gamePlayer.getUser().getRoomUnit())) {
            gamePlayer.getUser().getUserInfo().getCurrentRoom().giveEffect(gamePlayer.getUser(), 0, -1);
        }
        gamePlayer.getUser().getRoomUnit().setCanWalk(true);

        super.removeMember(gamePlayer);

        if (room != null && room.getRoomSpecialTypes() != null) {
            for (InteractionGameGate gate :
                    room.getRoomSpecialTypes().getBattleBanzaiGates().values()) {
                gate.updateState(game, 5);
            }
        }
    }
}
