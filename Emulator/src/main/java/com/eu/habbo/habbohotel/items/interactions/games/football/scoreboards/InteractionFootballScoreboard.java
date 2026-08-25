package com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameScoreboard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;

import java.sql.ResultSet;
import java.sql.SQLException;


public class InteractionFootballScoreboard extends InteractionGameScoreboard {
    private int score;

    public InteractionFootballScoreboard(ResultSet set, Item baseItem, GameTeamColors teamColor) throws SQLException {
        super(set, baseItem, teamColor);

        try {
            this.score = Integer.parseInt(this.getExtradata());
        } catch (Exception e) {
            this.score = 0;
        }
    }

    public InteractionFootballScoreboard(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells, GameTeamColors teamColor) {
        super(id, userId, item, extradata, limitedStack, limitedSells, teamColor);

        try {
            this.score = Integer.parseInt(extradata);
        } catch (Exception e) {
            this.score = 0;
        }
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }


    public int changeScore(int amount) {
        this.score += amount;

        if (this.score > 99) {
            this.score = 0;
        }

        if (this.score < 0) {
            this.score = 99;
        }

        this.setExtradata(this.score + "");
        this.needsUpdate(true);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null) {
            room.updateItem(this);
        }

        return this.score;
    }

    public int getScore() {
        return this.score;
    }

    public void setScore(int amount) {
        this.score = amount;

        if (this.score > 99) {
            this.score = 0;
        }

        if (this.score < 0) {
            this.score = 99;
        }

        this.setExtradata(this.score + "");
        this.needsUpdate(true);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null) {
            room.updateItem(this);
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (objects.length >= 1 && objects[0] instanceof Integer && client != null && !(objects.length >= 2 && objects[1] instanceof WiredEffectType)) {
            int state = (Integer) objects[0];

            switch (state) {
                case 1: {
                    this.changeScore(1);
                }
                break;

                case 2: {
                    this.changeScore(-1);
                }
                break;

                default:
                    this.setScore(0);
                    break;
            }
        } else {
            this.changeScore(1);
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }
}