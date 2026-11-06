package polaris.hotel.games.tag;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.games.Game;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.GameTeam;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.interactions.games.tag.InteractionTagField;
import polaris.hotel.items.interactions.games.tag.InteractionTagPole;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserItem;
import polaris.plugin.EventHandler;
import polaris.plugin.events.roomunit.RoomUnitLookAtPointEvent;
import polaris.plugin.events.users.UserTakeStepEvent;
import polaris.threading.runnables.UserItemNewState;

public abstract class TagGame extends Game {
    public Map<User, InteractionTagPole> taggers = new HashMap<>();

    public TagGame(Class<? extends GameTeam> gameTeamClazz, Class<? extends GamePlayer> gamePlayerClazz, Room room) {
        super(gameTeamClazz, gamePlayerClazz, room, false);
    }

    @EventHandler
    public static void onUserLookAtPoint(RoomUnitLookAtPointEvent event) {
        if (event.room == null || event.roomUnit == null || event.location == null) return;

        if (RoomLayout.tilesAdjecent(event.roomUnit.getCurrentLocation(), event.location)) {
            User user = event.room.getUser(event.roomUnit);

            if (user != null) {
                if (user.getUserInfo().getCurrentGame() != null) {
                    if (TagGame.class.isAssignableFrom(user.getUserInfo().getCurrentGame())) {
                        TagGame game =
                                (TagGame) event.room.getGame(user.getUserInfo().getCurrentGame());

                        if (game != null) {
                            if (game.isTagger(user)) {
                                for (User tagged : event.room.getUsersAt(event.location)) {
                                    if (tagged == user
                                            || tagged.getUserInfo().getCurrentGame() == null
                                            || tagged.getUserInfo().getCurrentGame()
                                                    != user.getUserInfo().getCurrentGame()) {
                                        continue;
                                    }

                                    game.tagged(event.room, user, tagged);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public static void onUserWalkEvent(UserTakeStepEvent event) {
        if (event.user.getUserInfo().getCurrentGame() != null
                && TagGame.class.isAssignableFrom(event.user.getUserInfo().getCurrentGame())) {
            Set<UserItem> items = event.user.getUserInfo().getCurrentRoom().getItemsAt(event.toLocation);

            TagGame game = (TagGame) event.user
                    .getUserInfo()
                    .getCurrentRoom()
                    .getGame(event.user.getUserInfo().getCurrentGame());

            if (game != null) {
                for (UserItem item : items) {
                    if (item instanceof InteractionTagField
                            && ((InteractionTagField) item).gameClazz
                                    == event.user.getUserInfo().getCurrentGame()) {
                        if (game.taggers.isEmpty()) {
                            game.tagged(event.user.getUserInfo().getCurrentRoom(), null, event.user);
                        }
                        return;
                    }
                }

                game.removeUser(event.user);
            }
        }
    }

    public abstract Class<? extends InteractionTagPole> getTagPole();

    public abstract int getMaleEffect();

    public abstract int getMaleTaggerEffect();

    public abstract int getFemaleEffect();

    public abstract int getFemaleTaggerEffect();

    public void tagged(Room room, User tagger, User tagged) {
        if (this.taggers.containsKey(tagged)) {
            return;
        }

        Set<UserItem> poles = room.getRoomSpecialTypes().getItemsOfType(this.getTagPole());
        InteractionTagPole pole = this.taggers.get(tagger);
        room.giveEffect(tagged, this.getTaggedEffect(tagged), -1);

        if (poles.size() > this.taggers.size()) {
            for (Map.Entry<User, InteractionTagPole> set : this.taggers.entrySet()) {
                poles.remove(set.getValue());
            }

            for (UserItem item : poles) {
                tagged.getUserInfo().getCurrentRoom().giveEffect(tagged, this.getTaggedEffect(tagged), -1);
                this.taggers.put(tagged, (InteractionTagPole) item);
            }
        } else {
            if (tagger != null) {
                room.giveEffect(tagger, this.getEffect(tagger), -1);
                this.taggers.remove(tagger);
            }

            this.taggers.put(tagged, pole);
        }

        if (pole != null) {
            pole.setExtradata("1");
            room.updateItemState(pole);
            Emulator.getThreading().run(new UserItemNewState(pole, room, "0"), 1000);
        }
    }

    @Override
    public synchronized boolean addUser(User user, GameTeamColors teamColor) {
        super.addUser(user, GameTeamColors.RED);

        if (this.getTagPole() != null) {
            Set<UserItem> poles =
                    user.getUserInfo().getCurrentRoom().getRoomSpecialTypes().getItemsOfType(this.getTagPole());

            if (poles.size() > this.taggers.size()) {
                for (Map.Entry<User, InteractionTagPole> set : this.taggers.entrySet()) {
                    poles.remove(set.getValue());
                }

                Iterator<UserItem> iterator = poles.iterator();
                if ((iterator.hasNext())) {
                    UserItem item = iterator.next();
                    user.getUserInfo().getCurrentRoom().giveEffect(user, this.getEffect(user), -1);
                    this.room.scheduledTasks.add(
                            () -> user.getUserInfo().getCurrentRoom().giveEffect(user, this.getTaggedEffect(user), -1));
                    this.taggers.put(user, (InteractionTagPole) item);
                    return true;
                }
            }
        } else {
            if (this.taggers.isEmpty()) {
                user.getUserInfo().getCurrentRoom().giveEffect(user, this.getEffect(user), -1);
                this.room.scheduledTasks.add(
                        () -> user.getUserInfo().getCurrentRoom().giveEffect(user, this.getTaggedEffect(user), -1));
                this.taggers.put(user, null);
                return true;
            }
        }

        user.getUserInfo().getCurrentRoom().giveEffect(user, this.getEffect(user), -1);

        return true;
    }

    @Override
    public synchronized void removeUser(User user) {
        super.removeUser(user);
        this.taggers.remove(user);
        user.getUserInfo().getCurrentRoom().giveEffect(user, 0, -1);
    }

    @Override
    public void initialise() {}

    @Override
    public void run() {}

    public int getEffect(User user) {
        if (user.getUserInfo().getGender().equals(UserGender.M)) {
            return this.getMaleEffect();
        }

        return this.getFemaleEffect();
    }

    public int getTaggedEffect(User user) {
        if (user.getUserInfo().getGender().equals(UserGender.M)) {
            return this.getMaleTaggerEffect();
        }

        return this.getFemaleTaggerEffect();
    }

    public boolean isTagger(User user) {
        return this.taggers.containsKey(user);
    }
}
