package polaris.hotel.items.interactions.games.football;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UpdateUserLookComposer;
import polaris.plugin.EventHandler;
import polaris.plugin.events.users.UserDisconnectEvent;
import polaris.plugin.events.users.UserExitRoomEvent;
import polaris.plugin.events.users.UserSavedLookEvent;
import polaris.util.figure.FigureUtil;

public class InteractionFootballGate extends UserItem {
    private static final String CACHE_KEY = "fball_gate_look";
    private String figureM;
    private String figureF;

    public InteractionFootballGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        String[] bits = set.getString("extra_data").split(";");
        this.figureM = bits.length > 0 ? bits[0] : "";
        this.figureF = bits.length > 1 ? bits[1] : "";
    }

    public InteractionFootballGate(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        String[] bits = extradata.split(";");
        this.figureM = bits.length > 0 ? bits[0] : "";
        this.figureF = bits.length > 1 ? bits[1] : "";
    }

    @EventHandler
    public static void onUserDisconnectEvent(UserDisconnectEvent event) {
        if (event.user != null) {
            removeLook(event.user);
        }
    }

    @EventHandler
    public static void onUserExitRoomEvent(UserExitRoomEvent event) {
        if (event.user != null) {
            removeLook(event.user);
        }
    }

    @EventHandler
    public static void onUserSavedLookEvent(UserSavedLookEvent event) {
        if (event.user != null) {
            removeLook(event.user);
        }
    }

    private static void removeLook(User user) {
        if (user.getUserStats().cache.containsKey(CACHE_KEY)) {
            user.getUserInfo().setLook((String) user.getUserStats().cache.get(CACHE_KEY));
            user.getUserStats().cache.remove(CACHE_KEY);
            user.getClient().sendResponse(new UpdateUserLookComposer(user));
            if (user.getUserInfo().getCurrentRoom() != null) {
                user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
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
        User user = room.getUser(roomUnit);
        if (user != null) {
            if (user.getUserStats().cache.containsKey(CACHE_KEY)) {
                String oldlook = (String) user.getUserStats().cache.get(CACHE_KEY);

                UserSavedLookEvent lookEvent =
                        new UserSavedLookEvent(user, user.getUserInfo().getGender(), oldlook);
                Emulator.getPluginManager().fireEvent(lookEvent);
                if (!lookEvent.isCancelled()) {
                    user.getUserInfo()
                            .setLook(
                                    ClothingValidationManager.VALIDATE_ON_FBALLGATE
                                            ? ClothingValidationManager.validateLook(
                                                    user, lookEvent.newLook, lookEvent.gender.name())
                                            : lookEvent.newLook);
                    Emulator.getThreading().run(user.getUserInfo());
                    user.getClient().sendResponse(new UpdateUserLookComposer(user));
                    room.sendComposer(new RoomUserDataComposer(user).compose());
                }

                user.getUserStats().cache.remove(CACHE_KEY);
            } else {
                String finalLook = FigureUtil.mergeFigures(
                        user.getUserInfo().getLook(),
                        user.getUserInfo().getGender() == UserGender.F ? this.figureF : this.figureM,
                        new String[] {"hd", "hr", "ha", "he", "ea", "fa"},
                        new String[] {"ch", "ca", "cc", "cp", "lg", "wa", "sh"});

                UserSavedLookEvent lookEvent =
                        new UserSavedLookEvent(user, user.getUserInfo().getGender(), finalLook);
                Emulator.getPluginManager().fireEvent(lookEvent);
                if (!lookEvent.isCancelled()) {
                    user.getUserStats().cache.put(CACHE_KEY, user.getUserInfo().getLook());
                    user.getUserInfo()
                            .setLook(
                                    ClothingValidationManager.VALIDATE_ON_FBALLGATE
                                            ? ClothingValidationManager.validateLook(
                                                    user, lookEvent.newLook, lookEvent.gender.name())
                                            : lookEvent.newLook);
                    Emulator.getThreading().run(user.getUserInfo());
                    user.getClient().sendResponse(new UpdateUserLookComposer(user));
                    room.sendComposer(new RoomUserDataComposer(user).compose());
                }
            }
        }

        super.onWalkOn(roomUnit, room, objects);
    }
}
