package com.eu.habbo.habbohotel.games.freeze;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameGate;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;

public class FreezeGameTeam extends GameTeam {
    public FreezeGameTeam(GameTeamColors teamColor) {
        super(teamColor);
    }

    @Override
    public void removeMember(GamePlayer gamePlayer) {
        if (gamePlayer == null
                || gamePlayer.getHabbo() == null
                || gamePlayer.getHabbo().getHabboInfo().getCurrentRoom() == null) return;

        Game game = gamePlayer.getHabbo().getHabboInfo().getCurrentRoom().getGame(FreezeGame.class);
        Room room = gamePlayer.getHabbo().getRoomUnit().getRoom();

        if (!WiredFreezeUtil.isFrozen(gamePlayer.getHabbo().getRoomUnit())) {
            gamePlayer.getHabbo().getHabboInfo().getCurrentRoom().giveEffect(gamePlayer.getHabbo(), 0, -1);
        }
        gamePlayer.getHabbo().getRoomUnit().setCanWalk(true);

        super.removeMember(gamePlayer);

        if (room != null && room.getRoomSpecialTypes() != null) {
            for (InteractionGameGate gate :
                    room.getRoomSpecialTypes().getFreezeGates().values()) {
                gate.updateState(game, 5);
            }
        }
    }

    @Override
    public void addMember(GamePlayer gamePlayer) {
        super.addMember(gamePlayer);

        if (!WiredFreezeUtil.isFrozen(gamePlayer.getHabbo().getRoomUnit())) {
            gamePlayer
                    .getHabbo()
                    .getHabboInfo()
                    .getCurrentRoom()
                    .giveEffect(gamePlayer.getHabbo(), FreezeGame.effectId + this.teamColor.type, -1);
        }
    }
}
