package com.eu.habbo.habbohotel.items.interactions.games.football;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UpdateUserLookComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.eu.habbo.plugin.events.users.UserSavedLookEvent;
import com.eu.habbo.util.figure.FigureUtil;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionFootballGate extends HabboItem {
    private static final String CACHE_KEY = "fball_gate_look";
    private String figureM;
    private String figureF;

    public InteractionFootballGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        String[] bits = set.getString("extra_data").split(";");
        this.figureM = bits.length > 0 ? bits[0] : "";
        this.figureF = bits.length > 1 ? bits[1] : "";
    }

    public InteractionFootballGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        String[] bits = extradata.split(";");
        this.figureM = bits.length > 0 ? bits[0] : "";
        this.figureF = bits.length > 1 ? bits[1] : "";
    }

    @EventHandler
    public static void onUserDisconnectEvent(UserDisconnectEvent event) {
        if (event.habbo != null) {
            removeLook(event.habbo);
        }
    }

    @EventHandler
    public static void onUserExitRoomEvent(UserExitRoomEvent event) {
        if (event.habbo != null) {
            removeLook(event.habbo);
        }
    }

    @EventHandler
    public static void onUserSavedLookEvent(UserSavedLookEvent event) {
        if (event.habbo != null) {
            removeLook(event.habbo);
        }
    }

    private static void removeLook(Habbo habbo) {
        if (habbo.getHabboStats().cache.containsKey(CACHE_KEY)) {
            habbo.getHabboInfo().setLook((String) habbo.getHabboStats().cache.get(CACHE_KEY));
            habbo.getHabboStats().cache.remove(CACHE_KEY);
            habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo));
            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
            }
        }
    }

    public void setFigureM(String look) {
        this.figureM = look;

        this.setExtradata(this.figureM + ";" + this.figureF);
        this.needsUpdate(true);
        Emulator.getThreading().run(this);
    }

    public void setFigureF(String look) {
        this.figureF = look;

        this.setExtradata(this.figureM + ";" + this.figureF);
        this.needsUpdate(true);
        Emulator.getThreading().run(this);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.figureM + "," + this.figureF);
        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null) {
            if (habbo.getHabboStats().cache.containsKey(CACHE_KEY)) {
                String oldlook = (String) habbo.getHabboStats().cache.get(CACHE_KEY);

                UserSavedLookEvent lookEvent = new UserSavedLookEvent(habbo, habbo.getHabboInfo().getGender(), oldlook);
                Emulator.getPluginManager().fireEvent(lookEvent);
                if (!lookEvent.isCancelled()) {
                    habbo.getHabboInfo().setLook(ClothingValidationManager.VALIDATE_ON_FBALLGATE ? ClothingValidationManager.validateLook(habbo, lookEvent.newLook, lookEvent.gender.name()) : lookEvent.newLook);
                    Emulator.getThreading().run(habbo.getHabboInfo());
                    habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo));
                    room.sendComposer(new RoomUserDataComposer(habbo).compose());
                }

                habbo.getHabboStats().cache.remove(CACHE_KEY);
            } else {
                String finalLook = FigureUtil.mergeFigures(habbo.getHabboInfo().getLook(), habbo.getHabboInfo().getGender() == HabboGender.F ? this.figureF : this.figureM, new String[]{"hd", "hr", "ha", "he", "ea", "fa"}, new String[]{"ch", "ca", "cc", "cp", "lg", "wa", "sh"});

                UserSavedLookEvent lookEvent = new UserSavedLookEvent(habbo, habbo.getHabboInfo().getGender(), finalLook);
                Emulator.getPluginManager().fireEvent(lookEvent);
                if (!lookEvent.isCancelled()) {
                    habbo.getHabboStats().cache.put(CACHE_KEY, habbo.getHabboInfo().getLook());
                    habbo.getHabboInfo().setLook(ClothingValidationManager.VALIDATE_ON_FBALLGATE ? ClothingValidationManager.validateLook(habbo, lookEvent.newLook, lookEvent.gender.name()) : lookEvent.newLook);
                    Emulator.getThreading().run(habbo.getHabboInfo());
                    habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo));
                    room.sendComposer(new RoomUserDataComposer(habbo).compose());
                }
            }
        }

        super.onWalkOn(roomUnit, room, objects);
    }
}