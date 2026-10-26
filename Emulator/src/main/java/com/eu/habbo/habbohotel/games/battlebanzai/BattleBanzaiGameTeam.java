package com.eu.habbo.habbohotel.games.battlebanzai;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameGate;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;

public class BattleBanzaiGameTeam extends GameTeam {
    public BattleBanzaiGameTeam(GameTeamColors teamColor) {
        super(teamColor);
    }

    @Override
    public void addMember(GamePlayer gamePlayer) {
        super.addMember(gamePlayer);

        if (!WiredFreezeUtil.isFrozen(gamePlayer.getHabbo().getRoomUnit())) {
            gamePlayer
                    .getHabbo()
                    .getHabboInfo()
                    .getCurrentRoom()
                    .giveEffect(gamePlayer.getHabbo(), BattleBanzaiGame.effectId + this.teamColor.type, -1);
        }
    }

    @Override
    public void removeMember(GamePlayer gamePlayer) {
        Game game = gamePlayer
                .getHabbo()
                .getHabboInfo()
                .getCurrentRoom()
                .getGame(gamePlayer.getHabbo().getHabboInfo().getCurrentGame());
        Room room = gamePlayer.getHabbo().getRoomUnit().getRoom();

        if (!WiredFreezeUtil.isFrozen(gamePlayer.getHabbo().getRoomUnit())) {
            gamePlayer.getHabbo().getHabboInfo().getCurrentRoom().giveEffect(gamePlayer.getHabbo(), 0, -1);
        }
        gamePlayer.getHabbo().getRoomUnit().setCanWalk(true);

        super.removeMember(gamePlayer);

        if (room != null && room.getRoomSpecialTypes() != null) {
            for (InteractionGameGate gate :
                    room.getRoomSpecialTypes().getBattleBanzaiGates().values()) {
                gate.updateState(game, 5);
            }
        }
    }
}
