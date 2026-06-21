package com.eu.habbo.habbohotel.games.tag;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagPole;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.roomunit.RoomUnitLookAtPointEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.eu.habbo.threading.runnables.HabboItemNewState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public abstract class TagGame extends Game {
    public Map<Habbo, InteractionTagPole> taggers = new HashMap<>();

    public TagGame(Class<? extends GameTeam> gameTeamClazz, Class<? extends GamePlayer> gamePlayerClazz, Room room) {
        super(gameTeamClazz, gamePlayerClazz, room, false);
    }

    @EventHandler
    public static void onUserLookAtPoint(RoomUnitLookAtPointEvent event) {
        if (event.room == null || event.roomUnit == null || event.location == null) return;

        if (RoomLayout.tilesAdjecent(event.roomUnit.getCurrentLocation(), event.location)) {
            Habbo habbo = event.room.getHabbo(event.roomUnit);

            if (habbo != null) {
                if (habbo.getHabboInfo().getCurrentGame() != null) {
                    if (TagGame.class.isAssignableFrom(habbo.getHabboInfo().getCurrentGame())) {
                        TagGame game = (TagGame) event.room.getGame(habbo.getHabboInfo().getCurrentGame());

                        if (game != null) {
                            if (game.isTagger(habbo)) {
                                for (Habbo tagged : event.room.getHabbosAt(event.location)) {
                                    if (tagged == habbo || tagged.getHabboInfo().getCurrentGame() == null || tagged.getHabboInfo().getCurrentGame() != habbo.getHabboInfo().getCurrentGame()) {
                                        continue;
                                    }

                                    game.tagged(event.room, habbo, tagged);
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
        if (event.habbo.getHabboInfo().getCurrentGame() != null && TagGame.class.isAssignableFrom(event.habbo.getHabboInfo().getCurrentGame())) {
            Set<HabboItem> items = event.habbo.getHabboInfo().getCurrentRoom().getItemsAt(event.toLocation);

            TagGame game = (TagGame) event.habbo.getHabboInfo().getCurrentRoom().getGame(event.habbo.getHabboInfo().getCurrentGame());

            if (game != null) {
                for (HabboItem item : items) {
                    if (item instanceof InteractionTagField && ((InteractionTagField) item).gameClazz == event.habbo.getHabboInfo().getCurrentGame()) {
                        if (game.taggers.isEmpty()) {
                            game.tagged(event.habbo.getHabboInfo().getCurrentRoom(), null, event.habbo);
                        }
                        return;
                    }
                }

                game.removeHabbo(event.habbo);
            }
        }
    }

    public abstract Class<? extends InteractionTagPole> getTagPole();

    public abstract int getMaleEffect();

    public abstract int getMaleTaggerEffect();

    public abstract int getFemaleEffect();

    public abstract int getFemaleTaggerEffect();

    public void tagged(Room room, Habbo tagger, Habbo tagged) {
        if (this.taggers.containsKey(tagged)) {
            return;
        }

        Set<HabboItem> poles = room.getRoomSpecialTypes().getItemsOfType(this.getTagPole());
        InteractionTagPole pole = this.taggers.get(tagger);
        room.giveEffect(tagged, this.getTaggedEffect(tagged), -1);

        if (poles.size() > this.taggers.size()) {
            for (Map.Entry<Habbo, InteractionTagPole> set : this.taggers.entrySet()) {
                poles.remove(set.getValue());
            }

            for (HabboItem item : poles) {
                tagged.getHabboInfo().getCurrentRoom().giveEffect(tagged, this.getTaggedEffect(tagged), -1);
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
            Emulator.getThreading().run(new HabboItemNewState(pole, room, "0"), 1000);
        }
    }

    @Override
    public synchronized boolean addHabbo(Habbo habbo, GameTeamColors teamColor) {
        super.addHabbo(habbo, GameTeamColors.RED);

        if (this.getTagPole() != null) {
            Set<HabboItem> poles = habbo.getHabboInfo().getCurrentRoom().getRoomSpecialTypes().getItemsOfType(this.getTagPole());

            if (poles.size() > this.taggers.size()) {
                for (Map.Entry<Habbo, InteractionTagPole> set : this.taggers.entrySet()) {
                    poles.remove(set.getValue());
                }

                Iterator<HabboItem> iterator = poles.iterator();
                if ((iterator.hasNext())) {
                    HabboItem item = iterator.next();
                    habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, this.getEffect(habbo), -1);
                    this.room.scheduledTasks.add(() -> habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, this.getTaggedEffect(habbo), -1));
                    this.taggers.put(habbo, (InteractionTagPole) item);
                    return true;
                }
            }
        } else {
            if (this.taggers.isEmpty()) {
                habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, this.getEffect(habbo), -1);
                this.room.scheduledTasks.add(() -> habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, this.getTaggedEffect(habbo), -1));
                this.taggers.put(habbo, null);
                return true;
            }
        }

        habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, this.getEffect(habbo), -1);

        return true;
    }

    @Override
    public synchronized void removeHabbo(Habbo habbo) {
        super.removeHabbo(habbo);
        this.taggers.remove(habbo);
        habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 0, -1);
    }

    @Override
    public void initialise() {

    }

    @Override
    public void run() {

    }

    public int getEffect(Habbo habbo) {
        if (habbo.getHabboInfo().getGender().equals(HabboGender.M)) {
            return this.getMaleEffect();
        }

        return this.getFemaleEffect();
    }

    public int getTaggedEffect(Habbo habbo) {
        if (habbo.getHabboInfo().getGender().equals(HabboGender.M)) {
            return this.getMaleTaggerEffect();
        }

        return this.getFemaleTaggerEffect();
    }

    public boolean isTagger(Habbo habbo) {
        return this.taggers.containsKey(habbo);
    }
}
