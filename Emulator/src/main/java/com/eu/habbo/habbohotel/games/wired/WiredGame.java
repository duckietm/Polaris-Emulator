package com.eu.habbo.habbohotel.games.wired;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

public class WiredGame extends Game {
    public static final int RED_EFFECT_ID = 223;
    public static final int BLUE_EFFECT_ID = 224;
    public static final int YELLOW_EFFECT_ID = 225;
    public static final int GREEN_EFFECT_ID = 226;
    public GameState state = GameState.RUNNING;

    public WiredGame(Room room) {
        super(GameTeam.class, GamePlayer.class, room, false);
    }

    @Override
    public void initialise() {
        this.state = GameState.RUNNING;

        for (GameTeam team : this.teams.values()) {
            team.resetScores();
        }
    }

    @Override
    public void run() {
        this.state = GameState.RUNNING;
    }

    @Override
    public boolean addHabbo(Habbo habbo, GameTeamColors teamColor) {
        this.room.giveEffect(habbo, this.getEffectId(teamColor), -1);
        return super.addHabbo(habbo, teamColor);
    }

    @Override
    public void removeHabbo(Habbo habbo) {
        super.removeHabbo(habbo);
        this.room.giveEffect(habbo, 0, -1);
    }

    @Override
    public void stop() {
        this.state = GameState.RUNNING;
    }

    @Override
    public GameState getState() {
        return GameState.RUNNING;
    }

    private int getEffectId(GameTeamColors teamColor) {
        return teamEffectId(teamColor);
    }

    /** The effect a member of a Wired team wears: 223 red, 224 blue, 225 yellow, 226 green. */
    public static int teamEffectId(GameTeamColors teamColor) {
        switch (teamColor) {
            case RED:
                return RED_EFFECT_ID;
            case BLUE:
                return BLUE_EFFECT_ID;
            case YELLOW:
                return YELLOW_EFFECT_ID;
            case GREEN:
                return GREEN_EFFECT_ID;
            default:
                return FreezeGame.effectId + teamColor.type;
        }
    }
}
