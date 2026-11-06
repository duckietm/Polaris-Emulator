package polaris.hotel.rooms;

import polaris.hotel.items.ICycleable;
import polaris.hotel.items.interactions.InteractionBlackHole;
import polaris.hotel.items.interactions.InteractionBuildArea;
import polaris.hotel.items.interactions.InteractionFireworks;
import polaris.hotel.items.interactions.InteractionJukeBox;
import polaris.hotel.items.interactions.InteractionMoodLight;
import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.hotel.items.interactions.InteractionMuteArea;
import polaris.hotel.items.interactions.InteractionObstacle;
import polaris.hotel.items.interactions.InteractionPyramid;
import polaris.hotel.items.interactions.InteractionQueueSpeedControl;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.items.interactions.InteractionSnowboardSlope;
import polaris.hotel.items.interactions.InteractionStickyPole;
import polaris.hotel.items.interactions.InteractionTalkingFurniture;
import polaris.hotel.items.interactions.InteractionTent;
import polaris.hotel.items.interactions.InteractionVoteCounter;
import polaris.hotel.items.interactions.InteractionWater;
import polaris.hotel.items.interactions.InteractionWaterItem;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.InteractionWiredDisableControl;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.InteractionWiredHighscore;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.games.InteractionGameGate;
import polaris.hotel.items.interactions.games.InteractionGameScoreboard;
import polaris.hotel.items.interactions.games.InteractionGameTimer;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import polaris.hotel.items.interactions.games.tag.InteractionTagField;
import polaris.hotel.items.interactions.games.tag.InteractionTagPole;
import polaris.hotel.items.interactions.pets.InteractionNest;
import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.items.interactions.pets.InteractionPetDrink;
import polaris.hotel.items.interactions.pets.InteractionPetFood;
import polaris.hotel.items.interactions.pets.InteractionPetToy;
import polaris.hotel.items.interactions.pets.InteractionPetTree;
import polaris.hotel.items.interactions.wired.extra.WiredBlob;
import polaris.hotel.items.interactions.wired.extra.WiredExtraContextVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.hotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableAddonImport;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFx;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableLeaderboard;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableReference;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.arrays.WiredArrayVariableDefinition;
import polaris.hotel.wired.arrays.WiredArrayVariableType;
import polaris.hotel.wired.core.WiredContextVariableSupport;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.label.WiredLabelSupport;
import polaris.hotel.wired.leaderboard.WiredLeaderboardSupport;
import polaris.hotel.wired.tick.WiredTickable;
import polaris.hotel.wired.variablefx.WiredVariableFxSupport;

final class RoomItemRegistry {

    private final Room room;

    RoomItemRegistry(Room room) {
        this.room = room;
    }

    void register(UserItem item) {
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

            // A variable fx box placed or loaded: the room's fx service starts telling players what to draw.
            // An add-on import box may bring fx from another room, which the same service draws.
            if (item instanceof WiredExtraVariableFx || item instanceof WiredExtraVariableAddonImport) {
                WiredVariableFxSupport.ensure(this.room);
            }

            // A leaderboard box placed or loaded: the room's leaderboard service starts sending panels.
            if (item instanceof WiredExtraVariableLeaderboard) {
                WiredLeaderboardSupport.ensure(this.room);
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
        this.room.onFurnitureTopologyChanged(item, true);

        // A furniture label placed: the players who may see it get it straight away.
        if (item instanceof WiredExtraLabel label) {
            WiredLabelSupport.broadcastChanged(this.room, label);
        }
    }

    void unregister(UserItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }

        if (item instanceof WiredExtraLabel) {
            WiredLabelSupport.broadcastRemoved(this.room, item.getId());
        }

        boolean cleanedSignalAntennaReferences =
                isAntennaItem(item) && specialTypes.unlinkSignalAntennaReferences(item.getId());
        this.room.getFurniVariableManager().removeAssignmentsForFurni(item.getId());
        this.room.getArrayVariableManager().removeOwner(WiredArrayVariableType.FURNI.code(), item.getId());

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
            specialTypes.removeExtra(extra);
            boolean broadcastDefinitions = this.removeExtraDefinitions(item);
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
        this.room.forgetWiredGravity(item);
        this.room.forgetWiredOpacity(item);
        RoomWiredRuntime wiredRuntime = this.room.getWiredRuntime();
        if (wiredRuntime != null) {
            wiredRuntime.forgetProjectile(item);
            wiredRuntime.forgetMovesFreely(item);
        }
        this.room.onFurnitureTopologyChanged(item, true);
    }

    /** Removes malformed wired furniture from every executable index without deleting the room item. */
    void quarantineWired(UserItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null || item == null) {
            return;
        }

        if (item instanceof WiredTickable tickable) {
            WiredManager.unregisterTickable(this.room, tickable);
        } else if (item instanceof ICycleable cycleable) {
            specialTypes.removeCycleTask(cycleable);
        }

        boolean wiredItem = false;
        if (item instanceof InteractionWiredTrigger trigger) {
            specialTypes.removeTrigger(trigger);
            wiredItem = true;
        } else if (item instanceof InteractionWiredEffect effect) {
            specialTypes.removeEffect(effect);
            wiredItem = true;
        } else if (item instanceof InteractionWiredCondition condition) {
            specialTypes.removeCondition(condition);
            wiredItem = true;
        } else if (item instanceof InteractionWiredExtra extra) {
            specialTypes.removeExtra(extra);
            wiredItem = true;
        }

        if (wiredItem) {
            WiredManager.invalidateRoom(this.room);
        }
    }

    private boolean removeExtraDefinitions(UserItem item) {
        boolean broadcastDefinitions = false;
        if (item instanceof WiredArrayVariableDefinition) {
            this.room.getArrayVariableManager().removeDefinition(item.getId());
        }
        if (item instanceof WiredExtraUserVariable) {
            this.room.getUserVariableManager().removeDefinition(item.getId());
        } else if (item instanceof WiredExtraFurniVariable) {
            this.room.getFurniVariableManager().removeDefinition(item.getId());
        } else if (item instanceof WiredExtraRoomVariable) {
            this.room.getRoomVariableManager().removeDefinition(item.getId());
        } else if (item instanceof WiredExtraContextVariable
                || item instanceof WiredExtraVariableTextConnector
                || item instanceof WiredExtraPointerVariable) {
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

    private static boolean isAntennaItem(UserItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionType = item.getBaseItem().getInteractionType().getName();
        return interactionType != null && interactionType.equalsIgnoreCase("antenna");
    }

    private static boolean isUndefinedSpecialType(UserItem item) {
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
                || item instanceof InteractionObstacle
                || item instanceof InteractionBlackHole
                || item instanceof InteractionWiredHighscore
                || item instanceof InteractionStickyPole
                || item instanceof WiredBlob
                || item instanceof InteractionTent
                || item instanceof InteractionSnowboardSlope
                || item instanceof InteractionFireworks
                || item instanceof InteractionVoteCounter
                || item instanceof InteractionWiredDisableControl
                || item instanceof InteractionQueueSpeedControl;
    }

    private static boolean isUndefinedSpecialTypeOnRemoval(UserItem item) {
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
                || item instanceof InteractionObstacle
                || item instanceof InteractionBlackHole
                || item instanceof InteractionWiredHighscore
                || item instanceof InteractionStickyPole
                || item instanceof WiredBlob
                || item instanceof InteractionTent
                || item instanceof InteractionSnowboardSlope
                || item instanceof InteractionVoteCounter
                || item instanceof InteractionWiredDisableControl
                || item instanceof InteractionQueueSpeedControl;
    }
}
