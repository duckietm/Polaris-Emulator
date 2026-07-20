package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.interactions.InteractionBlackHole;
import com.eu.habbo.habbohotel.items.interactions.InteractionBuildArea;
import com.eu.habbo.habbohotel.items.interactions.InteractionFireworks;
import com.eu.habbo.habbohotel.items.interactions.InteractionJukeBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionMoodLight;
import com.eu.habbo.habbohotel.items.interactions.InteractionMusicDisc;
import com.eu.habbo.habbohotel.items.interactions.InteractionMuteArea;
import com.eu.habbo.habbohotel.items.interactions.InteractionPyramid;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.items.interactions.InteractionSnowboardSlope;
import com.eu.habbo.habbohotel.items.interactions.InteractionStickyPole;
import com.eu.habbo.habbohotel.items.interactions.InteractionTalkingFurniture;
import com.eu.habbo.habbohotel.items.interactions.InteractionTent;
import com.eu.habbo.habbohotel.items.interactions.InteractionVoteCounter;
import com.eu.habbo.habbohotel.items.interactions.InteractionWater;
import com.eu.habbo.habbohotel.items.interactions.InteractionWaterItem;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameGate;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagPole;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetDrink;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetFood;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetToy;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetTree;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredBlob;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;

final class RoomItemRegistry {

    private final Room room;

    RoomItemRegistry(Room room) {
        this.room = room;
    }

    void register(HabboItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }

        boolean wiredItem = false;
        synchronized (specialTypes) {
            if (item instanceof WiredTickable tickable) {
                WiredManager.registerTickable(this.room, tickable);
            } else if (item instanceof ICycleable cycleable) {
                specialTypes.addCycleTask(cycleable);
            }

            if (item instanceof InteractionWiredTrigger trigger) {
                specialTypes.addTrigger(trigger);
                wiredItem = true;
            } else if (item instanceof InteractionWiredEffect effect) {
                specialTypes.addEffect(effect);
                wiredItem = true;
            } else if (item instanceof InteractionWiredCondition condition) {
                specialTypes.addCondition(condition);
                wiredItem = true;
            } else if (item instanceof InteractionWiredExtra extra) {
                specialTypes.addExtra(extra);
                wiredItem = true;
            } else if (item instanceof InteractionBattleBanzaiTeleporter teleporter) {
                specialTypes.addBanzaiTeleporter(teleporter);
            } else if (item instanceof InteractionRoller roller) {
                specialTypes.addRoller(roller);
            } else if (item instanceof InteractionGameScoreboard scoreboard) {
                specialTypes.addGameScoreboard(scoreboard);
            } else if (item instanceof InteractionGameGate gate) {
                specialTypes.addGameGate(gate);
            } else if (item instanceof InteractionGameTimer timer) {
                specialTypes.addGameTimer(timer);
            } else if (item instanceof InteractionFreezeExitTile exitTile) {
                specialTypes.addFreezeExitTile(exitTile);
            } else if (item instanceof InteractionNest nest) {
                specialTypes.addNest(nest);
            } else if (item instanceof InteractionPetDrink drink) {
                specialTypes.addPetDrink(drink);
            } else if (item instanceof InteractionPetFood food) {
                specialTypes.addPetFood(food);
            } else if (item instanceof InteractionPetToy toy) {
                specialTypes.addPetToy(toy);
            } else if (item instanceof InteractionPetTree tree) {
                specialTypes.addPetTree(tree);
            } else if (isUndefinedSpecialType(item)) {
                specialTypes.addUndefined(item);
            }
        }

        if (wiredItem) {
            WiredManager.invalidateRoom(this.room);
        }
    }

    void unregister(HabboItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }

        boolean cleanedSignalAntennaReferences =
                isAntennaItem(item) && specialTypes.unlinkSignalAntennaReferences(item.getId());
        this.room.getFurniVariableManager().removeAssignmentsForFurni(item.getId());

        boolean wiredItem = false;
        if (item instanceof WiredTickable tickable) {
            WiredManager.unregisterTickable(this.room, tickable);
        } else if (item instanceof ICycleable cycleable) {
            specialTypes.removeCycleTask(cycleable);
        }

        if (item instanceof InteractionBattleBanzaiTeleporter teleporter) {
            specialTypes.removeBanzaiTeleporter(teleporter);
        } else if (item instanceof InteractionWiredTrigger trigger) {
            specialTypes.removeTrigger(trigger);
            wiredItem = true;
        } else if (item instanceof InteractionWiredEffect effect) {
            specialTypes.removeEffect(effect);
            wiredItem = true;
        } else if (item instanceof InteractionWiredCondition condition) {
            specialTypes.removeCondition(condition);
            wiredItem = true;
        } else if (item instanceof InteractionWiredExtra extra) {
            boolean broadcastDefinitions = this.removeExtraDefinitions(item);
            specialTypes.removeExtra(extra);
            if (broadcastDefinitions) {
                WiredContextVariableSupport.broadcastDefinitions(this.room);
            }
            wiredItem = true;
        } else if (item instanceof InteractionRoller roller) {
            specialTypes.removeRoller(roller);
        } else if (item instanceof InteractionGameScoreboard scoreboard) {
            specialTypes.removeScoreboard(scoreboard);
        } else if (item instanceof InteractionGameGate gate) {
            specialTypes.removeGameGate(gate);
        } else if (item instanceof InteractionGameTimer timer) {
            specialTypes.removeGameTimer(timer);
        } else if (item instanceof InteractionFreezeExitTile exitTile) {
            specialTypes.removeFreezeExitTile(exitTile);
        } else if (item instanceof InteractionNest nest) {
            specialTypes.removeNest(nest);
        } else if (item instanceof InteractionPetDrink drink) {
            specialTypes.removePetDrink(drink);
        } else if (item instanceof InteractionPetFood food) {
            specialTypes.removePetFood(food);
        } else if (item instanceof InteractionPetToy toy) {
            specialTypes.removePetToy(toy);
        } else if (item instanceof InteractionPetTree tree) {
            specialTypes.removePetTree(tree);
        } else if (isUndefinedSpecialTypeOnRemoval(item)) {
            specialTypes.removeUndefined(item);
        }

        if (wiredItem || cleanedSignalAntennaReferences) {
            WiredManager.invalidateRoom(this.room);
        }
    }

    private boolean removeExtraDefinitions(HabboItem item) {
        boolean broadcastDefinitions = false;
        if (item instanceof WiredExtraUserVariable) {
            this.room.getUserVariableManager().removeDefinition(item.getId());
        } else if (item instanceof WiredExtraFurniVariable) {
            this.room.getFurniVariableManager().removeDefinition(item.getId());
        } else if (item instanceof WiredExtraRoomVariable) {
            this.room.getRoomVariableManager().removeDefinition(item.getId());
        } else if (item instanceof WiredExtraContextVariable || item instanceof WiredExtraVariableTextConnector) {
            broadcastDefinitions = true;
        } else if (item instanceof WiredExtraVariableReference reference) {
            if (reference.isRoomReference()) {
                this.room.getRoomVariableManager().removeDefinition(item.getId());
            } else {
                this.room.getUserVariableManager().removeDefinition(item.getId());
            }
        } else if (item instanceof WiredExtraVariableEcho echo) {
            if (echo.isRoomEcho()) {
                this.room.getRoomVariableManager().removeDefinition(item.getId());
            } else if (echo.isFurniEcho()) {
                this.room.getFurniVariableManager().removeDefinition(item.getId());
            } else {
                this.room.getUserVariableManager().removeDefinition(item.getId());
            }
        }

        return broadcastDefinitions;
    }

    private static boolean isAntennaItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionType = item.getBaseItem().getInteractionType().getName();
        return interactionType != null && interactionType.equalsIgnoreCase("antenna");
    }

    private static boolean isUndefinedSpecialType(HabboItem item) {
        return item instanceof InteractionMoodLight
                || item instanceof InteractionPyramid
                || item instanceof InteractionMusicDisc
                || item instanceof InteractionBattleBanzaiSphere
                || item instanceof InteractionTalkingFurniture
                || item instanceof InteractionWater
                || item instanceof InteractionWaterItem
                || item instanceof InteractionMuteArea
                || item instanceof InteractionBuildArea
                || item instanceof InteractionTagPole
                || item instanceof InteractionTagField
                || item instanceof InteractionJukeBox
                || item instanceof InteractionPetBreedingNest
                || item instanceof InteractionBlackHole
                || item instanceof InteractionWiredHighscore
                || item instanceof InteractionStickyPole
                || item instanceof WiredBlob
                || item instanceof InteractionTent
                || item instanceof InteractionSnowboardSlope
                || item instanceof InteractionFireworks
                || item instanceof InteractionVoteCounter;
    }

    private static boolean isUndefinedSpecialTypeOnRemoval(HabboItem item) {
        return item instanceof InteractionMoodLight
                || item instanceof InteractionPyramid
                || item instanceof InteractionMusicDisc
                || item instanceof InteractionBattleBanzaiSphere
                || item instanceof InteractionTalkingFurniture
                || item instanceof InteractionWaterItem
                || item instanceof InteractionWater
                || item instanceof InteractionMuteArea
                || item instanceof InteractionTagPole
                || item instanceof InteractionTagField
                || item instanceof InteractionJukeBox
                || item instanceof InteractionPetBreedingNest
                || item instanceof InteractionBlackHole
                || item instanceof InteractionWiredHighscore
                || item instanceof InteractionStickyPole
                || item instanceof WiredBlob
                || item instanceof InteractionTent
                || item instanceof InteractionSnowboardSlope
                || item instanceof InteractionVoteCounter;
    }
}
