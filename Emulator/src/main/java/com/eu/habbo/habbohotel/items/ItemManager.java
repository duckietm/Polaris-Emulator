package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHideControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionBackgroundToner;
import com.eu.habbo.habbohotel.items.interactions.InteractionBadgeDisplay;
import com.eu.habbo.habbohotel.items.interactions.InteractionBlackHole;
import com.eu.habbo.habbohotel.items.interactions.InteractionBuildArea;
import com.eu.habbo.habbohotel.items.interactions.InteractionCannon;
import com.eu.habbo.habbohotel.items.interactions.InteractionClothing;
import com.eu.habbo.habbohotel.items.interactions.InteractionColorPlate;
import com.eu.habbo.habbohotel.items.interactions.InteractionColorWheel;
import com.eu.habbo.habbohotel.items.interactions.InteractionConfInvisControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionCostumeHopper;
import com.eu.habbo.habbohotel.items.interactions.InteractionCrackable;
import com.eu.habbo.habbohotel.items.interactions.InteractionCrackableMaster;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionDice;
import com.eu.habbo.habbohotel.items.interactions.InteractionDiceDisableControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionDoorkickDisableControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectGiver;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectTile;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectToggle;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectVendingMachine;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectVendingMachineNoSides;
import com.eu.habbo.habbohotel.items.interactions.InteractionExternalImage;
import com.eu.habbo.habbohotel.items.interactions.InteractionFXBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionFireworks;
import com.eu.habbo.habbohotel.items.interactions.InteractionGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.items.interactions.InteractionGroupPressurePlate;
import com.eu.habbo.habbohotel.items.interactions.InteractionGuildFurni;
import com.eu.habbo.habbohotel.items.interactions.InteractionGuildGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionGymEquipment;
import com.eu.habbo.habbohotel.items.interactions.InteractionHabboClubGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionHabboClubHopper;
import com.eu.habbo.habbohotel.items.interactions.InteractionHabboClubTeleportTile;
import com.eu.habbo.habbohotel.items.interactions.InteractionHanditem;
import com.eu.habbo.habbohotel.items.interactions.InteractionHanditemBlockControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionHanditemTile;
import com.eu.habbo.habbohotel.items.interactions.InteractionHideWiredControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionHopper;
import com.eu.habbo.habbohotel.items.interactions.InteractionInformationTerminal;
import com.eu.habbo.habbohotel.items.interactions.InteractionJukeBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionLoveLock;
import com.eu.habbo.habbohotel.items.interactions.InteractionMannequin;
import com.eu.habbo.habbohotel.items.interactions.InteractionMonsterCrackable;
import com.eu.habbo.habbohotel.items.interactions.InteractionMoodLight;
import com.eu.habbo.habbohotel.items.interactions.InteractionMultiHeight;
import com.eu.habbo.habbohotel.items.interactions.InteractionMusicDisc;
import com.eu.habbo.habbohotel.items.interactions.InteractionMuteArea;
import com.eu.habbo.habbohotel.items.interactions.InteractionNoSidesVendingMachine;
import com.eu.habbo.habbohotel.items.interactions.InteractionObstacle;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.InteractionPressurePlate;
import com.eu.habbo.habbohotel.items.interactions.InteractionPuzzleBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionPyramid;
import com.eu.habbo.habbohotel.items.interactions.InteractionQueueSpeedControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionRandomState;
import com.eu.habbo.habbohotel.items.interactions.InteractionRedeemableSubscriptionBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionRentableSpace;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomAds;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomOMatic;
import com.eu.habbo.habbohotel.items.interactions.InteractionSnowboardSlope;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStickyPole;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitch;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitchRemoteControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionTalkingFurniture;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.items.interactions.InteractionTent;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileEffectProvider;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.items.interactions.InteractionTrap;
import com.eu.habbo.habbohotel.items.interactions.InteractionTrophy;
import com.eu.habbo.habbohotel.items.interactions.InteractionVendingMachine;
import com.eu.habbo.habbohotel.items.interactions.InteractionVikingCotie;
import com.eu.habbo.habbohotel.items.interactions.InteractionVoteCounter;
import com.eu.habbo.habbohotel.items.interactions.InteractionWater;
import com.eu.habbo.habbohotel.items.interactions.InteractionWaterItem;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredDisableControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.items.interactions.InteractionYoutubeTV;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiPuck;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTile;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateBlue;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateGreen;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateRed;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateYellow;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardBlue;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardGreen;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardRed;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardYellow;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootball;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootballGate;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionRebugFootball;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalBlue;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalGreen;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalRed;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoalYellow;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardBlue;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardGreen;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardRed;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardYellow;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeBlock;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeTile;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateBlue;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateGreen;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateRed;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.gates.InteractionFreezeGateYellow;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardBlue;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardGreen;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardRed;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardYellow;
import com.eu.habbo.habbohotel.items.interactions.games.tag.bunnyrun.InteractionBunnyrunField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.bunnyrun.InteractionBunnyrunPole;
import com.eu.habbo.habbohotel.items.interactions.games.tag.icetag.InteractionIceTagField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.icetag.InteractionIceTagPole;
import com.eu.habbo.habbohotel.items.interactions.games.tag.rollerskate.InteractionRollerskateField;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionMonsterPlantSeed;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetDrink;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetFood;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetToy;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetTrampoline;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetTree;
import com.eu.habbo.habbohotel.items.interactions.totems.InteractionTotemHead;
import com.eu.habbo.habbohotel.items.interactions.totems.InteractionTotemLegs;
import com.eu.habbo.habbohotel.items.interactions.totems.InteractionTotemPlanet;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionActorDir;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionChestHasItemType;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionChestHasItems;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionCounterTimeMatches;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionDateRangeActive;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFrozen;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFurniHaveFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFurniHaveHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFurniInRange;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFurniNotInRange;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionFurniTypeMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionGroupMember;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboCount;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboHasEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboHasHandItem;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboHasMinItems;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboHasRights;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboIsFemale;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboIsMale;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboLacksCredits;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboLacksDiamonds;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboLacksDuckets;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboNotHasRights;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboNotOwnsFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboOwnsBadge;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboOwnsFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHabboWearsBadge;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHasAltitude;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHasTag;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHasVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionLessTimeElapsed;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMatchDate;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMatchStatePosition;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMatchTime;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMoreTimeElapsed;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMottoContains;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMovementValidation;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotFrozen;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotFurniHaveFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotFurniHaveHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotFurniTypeMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHabboCount;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHabboHasEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHabboHasHandItem;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHabboOwnsBadge;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHabboWearsBadge;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHasTag;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotHasVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotInGroup;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotInTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotMatchStatePosition;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotSameHeight;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotTriggerOnFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotTriggererMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionNotUserPerformsAction;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionSameHeight;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionSelectionQuantity;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTeamHasRank;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTeamHasScore;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTeamMember;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTriggerOnFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTriggererMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionUserInRange;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionUserNotInRange;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionUserPerformsAction;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableAgeMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableValueMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractPayment;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractReward;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractTrade;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAddTag;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAdjustClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAlert;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAllUsersLeaveTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotClothes;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotFollowHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotGiveHandItem;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotTalk;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotTalkToHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotTeleport;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotWalkToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectCancelTransaction;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeFurniDirection;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectControlClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectForwardUserToRoom;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFreeze;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniOpacity;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniToUser;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveAchievement;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveBadge;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveCredits;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveCurrencyFromChest;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveDiamonds;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveDuckets;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveExperience;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveFurniFromChest;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveHandItem;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveLook;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveRespect;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveReward;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScore;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScoreToTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectInitTransaction;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectJoinTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectKickHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectLay;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectLeaveTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectLog;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMakeFastWalk;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMakeUserSay;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniAsGroup;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniAway;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniTo;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniTowards;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateUser;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveUserTiles;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMuteHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectNegativeLog;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectNegativeSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectNegativeShowMessage;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectNegativeTriggerStacks;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectOpenHabboPages;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectPlaceFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRelativeMove;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveBadge;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveTag;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectResetHighscores;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectResetTimers;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSayCommand;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSetAltitude;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSit;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTeleport;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleMoodlight;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleRandom;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTriggerStacks;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectUnfreeze;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectUserToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWalkToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWhisper;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredBlob;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecuteInOrder;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurniByVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUser;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUsersByVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMoveCarryUsers;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMoveNoAnimation;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovePhysics;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementAnimation;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementCurve;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuest;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuestChain;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandom;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextInputVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputFurniName;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUnseen;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniAltitude;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniArea;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniByType;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniNeighborhood;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniOnFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniPicks;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniWithVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectRemoteSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectScanChestFurniByType;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersArea;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersByAction;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersByName;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersByType;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersGroup;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersHandItem;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersNeighborhood;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersOnFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectUsersWithVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtSetTime;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtTimeLong;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerBotReachedFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerBotReachedHabbo;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerClockCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerFurniStateToggled;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerGameEnds;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerGameStarts;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksTile;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboEntersRoom;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboIdles;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboLeavesRoom;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboPerformsAction;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboStartsDancing;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboStopsDancing;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboUnidles;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboWalkOffFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboWalkOnFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeater;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterLong;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterShort;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerScoreAchieved;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTeamLoses;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTeamWins;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTransactionComplete;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTransactionFail;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerUsernameAsTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreManager;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadItemsManagerEvent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemManager.class);
    private static final String BASE_ITEMS_SQL = """
            SELECT id, sprite_id, public_name, item_name, type, width, length,
                   stack_height, allow_stack, allow_sit, allow_lay, allow_walk,
                   allow_gift, allow_trade, allow_recycle,
                   allow_marketplace_sell, allow_inventory_stack,
                   interaction_type, interaction_modes_count, vending_ids,
                   multiheight, customparams, effect_id_male, effect_id_female,
                   clothing_on_walk
            FROM items_base
            ORDER BY id DESC
            """;

    // Configuration. Loaded from database & updated accordingly.
    public static volatile boolean RECYCLER_ENABLED = true;

    private final Int2ObjectMap<Item> items;
    private final Int2ObjectMap<CrackableReward> crackableRewards;
    private final ItemInteractionRegistry interactionsList;
    private final Map<String, SoundTrack> soundTracks;
    private final YoutubeManager youtubeManager;
    private final WiredHighscoreManager highscoreManager;
    private final TreeMap<Integer, NewUserGift> newuserGifts;

    public ItemManager() {
        this.items = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        this.crackableRewards = new Int2ObjectOpenHashMap<>();
        this.interactionsList = new ItemInteractionRegistry();
        this.soundTracks = new HashMap<>();
        this.youtubeManager = new YoutubeManager();
        this.highscoreManager = new WiredHighscoreManager();
        this.newuserGifts = new TreeMap<>();
    }

    public void load() {
        Emulator.getPluginManager().fireEvent(new EmulatorLoadItemsManagerEvent());

        long millis = System.currentTimeMillis();

        this.loadItemInteractions();
        this.loadItems();
        this.loadCrackable();
        this.loadSoundTracks();
        this.youtubeManager.load();
        this.highscoreManager.load();
        this.loadNewUserGifts();

        LOGGER.info("Item Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    protected void loadItemInteractions() {
        this.interactionsList.add(new ItemInteraction("default", InteractionDefault.class));
        this.interactionsList.add(new ItemInteraction("gate", InteractionGate.class));
        this.interactionsList.add(new ItemInteraction("guild_furni", InteractionGuildFurni.class));
        this.interactionsList.add(new ItemInteraction("guild_gate", InteractionGuildGate.class));
        this.interactionsList.add(new ItemInteraction("background_toner", InteractionBackgroundToner.class));
        this.interactionsList.add(new ItemInteraction("badge_display", InteractionBadgeDisplay.class));
        this.interactionsList.add(new ItemInteraction("mannequin", InteractionMannequin.class));
        this.interactionsList.add(new ItemInteraction("ads_bg", InteractionRoomAds.class));
        this.interactionsList.add(new ItemInteraction("trophy", InteractionTrophy.class));
        this.interactionsList.add(new ItemInteraction("vendingmachine", InteractionVendingMachine.class));
        this.interactionsList.add(new ItemInteraction("pressureplate", InteractionPressurePlate.class));
        this.interactionsList.add(new ItemInteraction("colorplate", InteractionColorPlate.class));
        this.interactionsList.add(new ItemInteraction("multiheight", InteractionMultiHeight.class));
        this.interactionsList.add(new ItemInteraction("dice", InteractionDice.class));
        this.interactionsList.add(new ItemInteraction("colorwheel", InteractionColorWheel.class));
        this.interactionsList.add(new ItemInteraction("cannon", InteractionCannon.class));
        this.interactionsList.add(new ItemInteraction("teleport", InteractionTeleport.class));
        this.interactionsList.add(new ItemInteraction("teleporttile", InteractionTeleportTile.class));
        this.interactionsList.add(new ItemInteraction("crackable", InteractionCrackable.class));
        this.interactionsList.add(new ItemInteraction("crackable_master", InteractionCrackableMaster.class));
        this.interactionsList.add(new ItemInteraction("nest", InteractionNest.class));
        this.interactionsList.add(new ItemInteraction("pet_drink", InteractionPetDrink.class));
        this.interactionsList.add(new ItemInteraction("pet_food", InteractionPetFood.class));
        this.interactionsList.add(new ItemInteraction("pet_toy", InteractionPetToy.class));
        this.interactionsList.add(new ItemInteraction("pet_tree", InteractionPetTree.class));
        this.interactionsList.add(new ItemInteraction("pet_trampoline", InteractionPetTrampoline.class));
        this.interactionsList.add(new ItemInteraction("breeding_nest", InteractionPetBreedingNest.class));
        this.interactionsList.add(new ItemInteraction("obstacle", InteractionObstacle.class));
        this.interactionsList.add(new ItemInteraction("monsterplant_seed", InteractionMonsterPlantSeed.class));
        this.interactionsList.add(new ItemInteraction("gift", InteractionGift.class));
        this.interactionsList.add(new ItemInteraction("stack_helper", InteractionStackHelper.class));
        this.interactionsList.add(new ItemInteraction("stack_walk_helper", InteractionStackWalkHelper.class));
        this.interactionsList.add(new ItemInteraction("puzzle_box", InteractionPuzzleBox.class));
        this.interactionsList.add(new ItemInteraction("hopper", InteractionHopper.class));
        this.interactionsList.add(new ItemInteraction("costume_hopper", InteractionCostumeHopper.class));
        this.interactionsList.add(new ItemInteraction("effect_gate", InteractionEffectGate.class));
        this.interactionsList.add(new ItemInteraction("club_hopper", InteractionHabboClubHopper.class));
        this.interactionsList.add(new ItemInteraction("club_gate", InteractionHabboClubGate.class));
        this.interactionsList.add(new ItemInteraction("club_teleporttile", InteractionHabboClubTeleportTile.class));
        this.interactionsList.add(new ItemInteraction("onewaygate", InteractionOneWayGate.class));
        this.interactionsList.add(new ItemInteraction("love_lock", InteractionLoveLock.class));
        this.interactionsList.add(new ItemInteraction("clothing", InteractionClothing.class));
        this.interactionsList.add(new ItemInteraction("roller", InteractionRoller.class));
        this.interactionsList.add(new ItemInteraction("postit", InteractionPostIt.class));
        this.interactionsList.add(new ItemInteraction("dimmer", InteractionMoodLight.class));
        this.interactionsList.add(new ItemInteraction("rentable_space", InteractionRentableSpace.class));
        this.interactionsList.add(new ItemInteraction("pyramid", InteractionPyramid.class));
        this.interactionsList.add(new ItemInteraction("musicdisc", InteractionMusicDisc.class));
        this.interactionsList.add(new ItemInteraction("fireworks", InteractionFireworks.class));
        this.interactionsList.add(new ItemInteraction("talking_furni", InteractionTalkingFurniture.class));
        this.interactionsList.add(new ItemInteraction("water_item", InteractionWaterItem.class));
        this.interactionsList.add(new ItemInteraction("water", InteractionWater.class));
        this.interactionsList.add(new ItemInteraction("viking_cotie", InteractionVikingCotie.class));
        this.interactionsList.add(new ItemInteraction("tile_fxprovider_nfs", InteractionTileEffectProvider.class));
        this.interactionsList.add(new ItemInteraction("mutearea", InteractionMuteArea.class));
        this.interactionsList.add(new ItemInteraction("buildarea", InteractionBuildArea.class));
        this.interactionsList.add(new ItemInteraction("information_terminal", InteractionInformationTerminal.class));
        this.interactionsList.add(new ItemInteraction("external_image", InteractionExternalImage.class));
        this.interactionsList.add(new ItemInteraction("youtube", InteractionYoutubeTV.class));
        this.interactionsList.add(new ItemInteraction("jukebox", InteractionJukeBox.class));
        this.interactionsList.add(new ItemInteraction("switch", InteractionSwitch.class));
        this.interactionsList.add(new ItemInteraction("conf_invis_control", InteractionConfInvisControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_invis_control", InteractionConfInvisControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_area_hide", InteractionAreaHideControl.class));
        this.interactionsList.add(new ItemInteraction("conf_area_hide", InteractionAreaHideControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_handitem_block", InteractionHanditemBlockControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_queue_speed", InteractionQueueSpeedControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_wired_disable", InteractionWiredDisableControl.class));
        this.interactionsList.add(new ItemInteraction("conf_hidewired", InteractionHideWiredControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_hidewired", InteractionHideWiredControl.class));
        this.interactionsList.add(new ItemInteraction("conf_dice_disable", InteractionDiceDisableControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_dice_disable", InteractionDiceDisableControl.class));
        this.interactionsList.add(
                new ItemInteraction("conf_doorkick_disable", InteractionDoorkickDisableControl.class));
        this.interactionsList.add(
                new ItemInteraction("wf_conf_doorkick_disable", InteractionDoorkickDisableControl.class));
        this.interactionsList.add(new ItemInteraction("switch_remote_control", InteractionSwitchRemoteControl.class));
        this.interactionsList.add(new ItemInteraction("fx_box", InteractionFXBox.class));
        this.interactionsList.add(new ItemInteraction("blackhole", InteractionBlackHole.class));
        this.interactionsList.add(new ItemInteraction("effect_toggle", InteractionEffectToggle.class));
        this.interactionsList.add(new ItemInteraction("room_o_matic", InteractionRoomOMatic.class));
        this.interactionsList.add(new ItemInteraction("effect_tile", InteractionEffectTile.class));
        this.interactionsList.add(new ItemInteraction("sticky_pole", InteractionStickyPole.class));
        this.interactionsList.add(new ItemInteraction("trap", InteractionTrap.class));
        this.interactionsList.add(new ItemInteraction("tent", InteractionTent.class));
        this.interactionsList.add(new ItemInteraction("gym_equipment", InteractionGymEquipment.class));
        this.interactionsList.add(new ItemInteraction("handitem", InteractionHanditem.class));
        this.interactionsList.add(new ItemInteraction("handitem_tile", InteractionHanditemTile.class));
        this.interactionsList.add(new ItemInteraction("effect_giver", InteractionEffectGiver.class));
        this.interactionsList.add(new ItemInteraction("effect_vendingmachine", InteractionEffectVendingMachine.class));
        this.interactionsList.add(
                new ItemInteraction("effect_vendingmachine_no_sides", InteractionEffectVendingMachineNoSides.class));
        this.interactionsList.add(new ItemInteraction("crackable_monster", InteractionMonsterCrackable.class));
        this.interactionsList.add(new ItemInteraction("snowboard_slope", InteractionSnowboardSlope.class));
        this.interactionsList.add(new ItemInteraction("pressureplate_group", InteractionGroupPressurePlate.class));
        this.interactionsList.add(new ItemInteraction("effect_tile_group", InteractionEffectTile.class));
        this.interactionsList.add(
                new ItemInteraction("crackable_subscription_box", InteractionRedeemableSubscriptionBox.class));
        this.interactionsList.add(new ItemInteraction("random_state", InteractionRandomState.class));
        this.interactionsList.add(
                new ItemInteraction("vendingmachine_no_sides", InteractionNoSidesVendingMachine.class));
        this.interactionsList.add(new ItemInteraction("tile_walkmagic", InteractionTileWalkMagic.class));
        this.interactionsList.add(new ItemInteraction("antenna", InteractionDefault.class));
        this.interactionsList.add(new ItemInteraction("room_invisible_click_tile", InteractionDefault.class));

        this.interactionsList.add(new ItemInteraction("game_timer", InteractionGameTimer.class));
        this.interactionsList.add(new ItemInteraction("game_upcounter", InteractionGameUpCounter.class));

        this.interactionsList.add(new ItemInteraction("wf_trg_walks_on_furni", WiredTriggerHabboWalkOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_walks_off_furni", WiredTriggerHabboWalkOffFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_furni", WiredTriggerHabboClicksFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_tile", WiredTriggerHabboClicksTile.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_user", WiredTriggerHabboClicksUser.class));
        this.interactionsList.add(
                new ItemInteraction("wf_trg_user_performs_action", WiredTriggerHabboPerformsAction.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_enter_room", WiredTriggerHabboEntersRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_leave_room", WiredTriggerHabboLeavesRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_says_something", WiredTriggerHabboSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_clock_counter", WiredTriggerClockCounter.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_var_changed", WiredTriggerVariableChanged.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_periodically", WiredTriggerRepeater.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_period_short", WiredTriggerRepeaterShort.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_period_long", WiredTriggerRepeaterLong.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_state_changed", WiredTriggerFurniStateToggled.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_stuff_state", WiredTriggerFurniStateToggled.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_at_given_time", WiredTriggerAtSetTime.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_at_time_long", WiredTriggerAtTimeLong.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_collision", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_starts", WiredTriggerGameStarts.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_ends", WiredTriggerGameEnds.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_bot_reached_stf", WiredTriggerBotReachedFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_bot_reached_avtr", WiredTriggerBotReachedHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_score_achieved", WiredTriggerScoreAchieved.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_team_win", WiredTriggerTeamWins.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_team_lose", WiredTriggerTeamLoses.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_recv_signal", WiredTriggerReceiveSignal.class));
        // Phase-C triggers (dance/idle)
        this.interactionsList.add(new ItemInteraction("wf_trg_starts_dancing", WiredTriggerHabboStartsDancing.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_stops_dancing", WiredTriggerHabboStopsDancing.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_idles", WiredTriggerHabboIdles.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_unidles", WiredTriggerHabboUnidles.class));
        // Phase-C effects (currency)
        this.interactionsList.add(new ItemInteraction("wf_act_give_credits", WiredEffectGiveCredits.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_duckets", WiredEffectGiveDuckets.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_diamonds", WiredEffectGiveDiamonds.class));
        // Phase-C effects (badges / achievements / posture / movement / misc)
        this.interactionsList.add(new ItemInteraction("wf_act_give_badge", WiredEffectGiveBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_userbadge", WiredEffectGiveBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_badge", WiredEffectRemoveBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_achievement", WiredEffectGiveAchievement.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_experience", WiredEffectGiveExperience.class));
        this.interactionsList.add(new ItemInteraction("wf_act_say_command", WiredEffectSayCommand.class));
        this.interactionsList.add(new ItemInteraction("wf_act_open_habbo_pages", WiredEffectOpenHabboPages.class));
        this.interactionsList.add(new ItemInteraction("wf_act_make_user_say", WiredEffectMakeUserSay.class));
        this.interactionsList.add(new ItemInteraction("wf_act_log", WiredEffectLog.class));
        this.interactionsList.add(new ItemInteraction("wf_act_walk_to_furni", WiredEffectWalkToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_sit", WiredEffectSit.class));
        this.interactionsList.add(new ItemInteraction("wf_act_lay", WiredEffectLay.class));
        this.interactionsList.add(new ItemInteraction("wf_act_make_fast_walk", WiredEffectMakeFastWalk.class));
        this.interactionsList.add(new ItemInteraction("wf_act_toggle_moodlight", WiredEffectToggleMoodlight.class));
        this.interactionsList.add(new ItemInteraction("wf_act_reset_highscores", WiredEffectResetHighscores.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_user_tiles", WiredEffectMoveUserTiles.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_all_users_leave_team", WiredEffectAllUsersLeaveTeam.class));
        // Phase-C conditions (currency / freeze / furni-range / same-height)
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_has_credits", WiredConditionHabboLacksCredits.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_has_diamonds", WiredConditionHabboLacksDiamonds.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_has_duckets", WiredConditionHabboLacksDuckets.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_freeze", WiredConditionFrozen.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_freeze", WiredConditionNotFrozen.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_furni_in_range", WiredConditionFurniInRange.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_furni_not_in_range", WiredConditionFurniNotInRange.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_same_height", WiredConditionSameHeight.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_has_same_height", WiredConditionNotSameHeight.class));
        // owns_furni: check the triggerer's inventory for the picked furni type(s) (reuse HAS_ALTITUDE picker).
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_owns_furni", WiredConditionHabboOwnsFurni.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_habbo_not_owns_furni", WiredConditionHabboNotOwnsFurni.class));
        // Negative-branch effects (run when the stack's conditions FAIL); reuse the SHOW_MESSAGE dialog.
        this.interactionsList.add(new ItemInteraction("wf_act_neg_show_message", WiredEffectNegativeShowMessage.class));
        this.interactionsList.add(new ItemInteraction("wf_act_neg_log", WiredEffectNegativeLog.class));
        // give_look exists client-side (FurnitureData): set the user's figure (text dialog = figure string).
        this.interactionsList.add(new ItemInteraction("wf_act_give_look", WiredEffectGiveLook.class));
        // Profile tags (text dialog = the tag; persisted via the new HabboStats tag helpers).
        this.interactionsList.add(new ItemInteraction("wf_act_add_tag", WiredEffectAddTag.class));
        this.interactionsList.add(new ItemInteraction("wf_act_add_tag_perm", WiredEffectAddTag.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_tag", WiredEffectRemoveTag.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_tag", WiredConditionHasTag.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_has_tag", WiredConditionNotHasTag.class));
        // Identity conditions reusing a meaningful existing dialog field (text = motto / int = item count).
        this.interactionsList.add(new ItemInteraction("wf_cnd_motto_contains", WiredConditionMottoContains.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_habbo_has_at_least_x_items", WiredConditionHabboHasMinItems.class));
        // OWNED-badge check (Phase A skipped these because only a worn-badge class existed): reuses the
        // wears-badge dialog but checks inventory ownership via BadgesComponent.hasBadge.
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_owns_badge", WiredConditionHabboOwnsBadge.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_owns_badge", WiredConditionNotHabboOwnsBadge.class));

        this.interactionsList.add(new ItemInteraction("wf_act_toggle_state", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_reset_timers", WiredEffectResetTimers.class));
        this.interactionsList.add(new ItemInteraction("wf_act_match_to_sshot", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_rotate", WiredEffectMoveRotateFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_score", WiredEffectGiveScore.class));
        this.interactionsList.add(new ItemInteraction("wf_act_show_message", WiredEffectWhisper.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_to", WiredEffectTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_join_team", WiredEffectJoinTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_act_leave_team", WiredEffectLeaveTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_act_chase", WiredEffectMoveFurniTowards.class));
        this.interactionsList.add(new ItemInteraction("wf_act_flee", WiredEffectMoveFurniAway.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_to_dir", WiredEffectChangeFurniDirection.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_score_tm", WiredEffectGiveScoreToTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_act_toggle_to_rnd", WiredEffectToggleRandom.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_furni_to", WiredEffectMoveFurniTo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_reward", WiredEffectGiveReward.class));
        this.interactionsList.add(new ItemInteraction("wf_act_call_stacks", WiredEffectTriggerStacks.class));
        this.interactionsList.add(new ItemInteraction("wf_act_neg_call_stack", WiredEffectNegativeTriggerStacks.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_neg_call_stacks", WiredEffectNegativeTriggerStacks.class));
        this.interactionsList.add(new ItemInteraction("wf_act_kick_user", WiredEffectKickHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_mute_triggerer", WiredEffectMuteHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_teleport", WiredEffectBotTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_move", WiredEffectBotWalkToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_talk", WiredEffectBotTalk.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_give_handitem", WiredEffectBotGiveHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_follow_avatar", WiredEffectBotFollowHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_clothes", WiredEffectBotClothes.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_talk_to_avatar", WiredEffectBotTalkToHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_respect", WiredEffectGiveRespect.class));
        this.interactionsList.add(new ItemInteraction("wf_act_alert", WiredEffectAlert.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_handitem", WiredEffectGiveHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_effect", WiredEffectGiveEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_act_freeze", WiredEffectFreeze.class));
        this.interactionsList.add(new ItemInteraction("wf_act_unfreeze", WiredEffectUnfreeze.class));
        this.interactionsList.add(new ItemInteraction("wf_act_furni_to_user", WiredEffectFurniToUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_user_to_furni", WiredEffectUserToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_furni_to_furni", WiredEffectFurniToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_set_altitude", WiredEffectSetAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_act_rel_mov", WiredEffectRelativeMove.class));
        this.interactionsList.add(new ItemInteraction("wf_act_control_clock", WiredEffectControlClock.class));
        this.interactionsList.add(new ItemInteraction("wf_act_adjust_clock", WiredEffectAdjustClock.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_rotate_user", WiredEffectMoveRotateUser.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_area", WiredEffectFurniArea.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_neighborhood", WiredEffectFurniNeighborhood.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_bytype", WiredEffectFurniByType.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_altitude", WiredEffectFurniAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_onfurni", WiredEffectFurniOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_picks", WiredEffectFurniPicks.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_signal", WiredEffectFurniSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_area", WiredEffectUsersArea.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_neighborhood", WiredEffectUsersNeighborhood.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_signal", WiredEffectUsersSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_bytype", WiredEffectUsersByType.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_team", WiredEffectUsersTeam.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_byaction", WiredEffectUsersByAction.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_byname", WiredEffectUsersByName.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_handitem", WiredEffectUsersHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_onfurni", WiredEffectUsersOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_group", WiredEffectUsersGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_furni_with_var", WiredEffectFurniWithVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_users_with_var", WiredEffectUsersWithVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_act_send_signal", WiredEffectSendSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_act_neg_send_signal", WiredEffectNegativeSendSignal.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_var", WiredEffectGiveVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_var", WiredEffectRemoveVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_act_change_var_val", WiredEffectChangeVariableValue.class));
        this.interactionsList.add(new ItemInteraction("wf_act_furni_opacity", WiredEffectFurniOpacity.class));

        this.interactionsList.add(new ItemInteraction("wf_cnd_has_furni_on", WiredConditionFurniHaveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_furnis_hv_avtrs", WiredConditionFurniHaveHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_stuff_is", WiredConditionFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_in_group", WiredConditionGroupMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_count_in", WiredConditionHabboCount.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wearing_effect", WiredConditionHabboHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wearing_badge", WiredConditionHabboWearsBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_time_less_than", WiredConditionLessTimeElapsed.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_match_snapshot", WiredConditionMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_time_more_than", WiredConditionMoreTimeElapsed.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_furni_on", WiredConditionNotFurniHaveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_hv_avtrs", WiredConditionNotFurniHaveHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_stuff_is", WiredConditionNotFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_user_count", WiredConditionNotHabboCount.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_wearing_fx", WiredConditionNotHabboHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_wearing_b", WiredConditionNotHabboWearsBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_in_group", WiredConditionNotInGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_in_team", WiredConditionNotInTeam.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_match_snap", WiredConditionNotMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_trggrer_on", WiredConditionNotTriggerOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_in_team", WiredConditionTeamMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_trggrer_on_frn", WiredConditionTriggerOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_handitem", WiredConditionHabboHasHandItem.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_has_handitem", WiredConditionNotHabboHasHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_date_rng_active", WiredConditionDateRangeActive.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_valid_moves", WiredConditionMovementValidation.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_counter_time_matches", WiredConditionCounterTimeMatches.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_match_time", WiredConditionMatchTime.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_match_date", WiredConditionMatchDate.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_dir", WiredConditionActorDir.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_slc_quantity", WiredConditionSelectionQuantity.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_user_performs_action", WiredConditionUserPerformsAction.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_user_performs_action", WiredConditionNotUserPerformsAction.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_altitude", WiredConditionHasAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_triggerer_match", WiredConditionTriggererMatch.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_triggerer_match", WiredConditionNotTriggererMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_team_has_score", WiredConditionTeamHasScore.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_team_has_rank", WiredConditionTeamHasRank.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_var", WiredConditionHasVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_neg_has_var", WiredConditionNotHasVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_var_val_match", WiredConditionVariableValueMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_var_age_match", WiredConditionVariableAgeMatch.class));
        // Player-facing wired chest (Scrigno) — currency + furni storage
        this.interactionsList.add(new ItemInteraction("wf_storage_coins1", InteractionWiredChestCurrency.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_coins2", InteractionWiredChestCurrency.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_furni1", InteractionWiredChestFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_furni2", InteractionWiredChestFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_furni_starter", InteractionWiredChestFurni.class));

        // Wired chest trading / contracts (Nitro layout codes 99–105, 110–113, 47–48, 27–28)
        this.interactionsList.add(new ItemInteraction("wf_act_give_currency", WiredEffectGiveCurrencyFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_furni", WiredEffectGiveFurniFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_act_init_transaction", WiredEffectInitTransaction.class));
        this.interactionsList.add(new ItemInteraction("wf_act_cancel_transaction", WiredEffectCancelTransaction.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_chest_has_items", WiredConditionChestHasItems.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_chest_has_item_type", WiredConditionChestHasItemType.class));
        this.interactionsList.add(
                new ItemInteraction("wf_trg_transaction_complete", WiredTriggerTransactionComplete.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_transaction_fail", WiredTriggerTransactionFail.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_payment", InteractionWiredContract.Payment.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_reward", InteractionWiredContract.Reward.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_trade", InteractionWiredContract.Trade.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_custom_contract", InteractionWiredContract.Custom.class));
        // Phase-2 chest-full wired: give-from-chest, has-item conditions, scanner, transactions,
        // place/remove-furni, contracts, quests (chest storage furni above are from the #291 base).
        this.interactionsList.add(new ItemInteraction("wf_act_give_currency", WiredEffectGiveCurrencyFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_chest_has_items", WiredConditionChestHasItems.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_furni", WiredEffectGiveFurniFromChest.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_chest_has_item_type", WiredConditionChestHasItemType.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_scan_chest_furni_by_type", WiredEffectScanChestFurniByType.class));
        this.interactionsList.add(new ItemInteraction("wf_act_init_transaction", WiredEffectInitTransaction.class));
        this.interactionsList.add(new ItemInteraction("wf_act_cancel_transaction", WiredEffectCancelTransaction.class));
        this.interactionsList.add(
                new ItemInteraction("wf_trg_transaction_complete", WiredTriggerTransactionComplete.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_transaction_fail", WiredTriggerTransactionFail.class));
        this.interactionsList.add(new ItemInteraction("wf_act_place_furni", WiredEffectPlaceFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_furni", WiredEffectRemoveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_payment", InteractionWiredContractPayment.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_reward", InteractionWiredContractReward.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_trade", InteractionWiredContractTrade.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_custom_contract", InteractionWiredCustomContract.class));
        this.interactionsList.add(new ItemInteraction("wf_var_quest", WiredExtraQuest.class));
        this.interactionsList.add(new ItemInteraction("wf_var_quest_chain", WiredExtraQuestChain.class));

        this.interactionsList.add(new ItemInteraction("wf_xtra_random", WiredExtraRandom.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_unseen", WiredExtraUnseen.class));
        this.interactionsList.add(new ItemInteraction("wf_blob", WiredBlob.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_or_eval", WiredExtraOrEval.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_filter_furni", WiredExtraFilterFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_filter_user", WiredExtraFilterUser.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_filter_users", WiredExtraFilterUser.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_filter_furni_by_var", WiredExtraFilterFurniByVariable.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_filter_users_by_var", WiredExtraFilterUsersByVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_carry_users", WiredExtraMoveCarryUsers.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_no_animation", WiredExtraMoveNoAnimation.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_anim_time", WiredExtraAnimationTime.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_physics", WiredExtraMovePhysics.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_animation", WiredExtraMovementAnimation.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_exec_in_order", WiredExtraExecuteInOrder.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_execution_limit", WiredExtraExecutionLimit.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_text_output_username", WiredExtraTextOutputUsername.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_text_output_furni_name", WiredExtraTextOutputFurniName.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_text_output_variable", WiredExtraTextOutputVariable.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_text_input_variable", WiredExtraTextInputVariable.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_var_text_connector", WiredExtraVariableTextConnector.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_var_lvlup_system", WiredExtraVariableLevelUpSystem.class));
        this.interactionsList.add(new ItemInteraction("wf_var_user", WiredExtraUserVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_furni", WiredExtraFurniVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_room", WiredExtraRoomVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_context", WiredExtraContextVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_reference", WiredExtraVariableReference.class));
        this.interactionsList.add(new ItemInteraction("wf_var_echo", WiredExtraVariableEcho.class));

        // ---- Inert-furni group: Group A/B + Phase-A aliases + advanced add-ons ----
        this.interactionsList.add(new ItemInteraction("wf_act_dont_chase", WiredEffectMoveFurniAway.class));
        this.interactionsList.add(new ItemInteraction("wf_act_dont_chase_top", WiredEffectMoveFurniAway.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_score_room", WiredEffectGiveScore.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_score_pp", WiredEffectGiveScore.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_bot_give_handitem_or_effect", WiredEffectBotGiveHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_all", WiredEffectTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_red", WiredEffectTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_green", WiredEffectTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_blue", WiredEffectTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_yellow", WiredEffectTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_is_male", WiredConditionHabboIsMale.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_is_female", WiredConditionHabboIsFemale.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_has_rights", WiredConditionHabboHasRights.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_has_rights", WiredConditionHabboNotHasRights.class));
        this.interactionsList.add(new ItemInteraction("wf_act_set_state", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_set_trg_state", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_open_gates", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_close_dice", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_close_gates", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_color_furni", WiredEffectToggleFurni.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_move_furni_from_stack", WiredEffectMoveRotateFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_rotate_no_under", WiredEffectMoveRotateFurni.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_allign_furni_stack", WiredEffectChangeFurniDirection.class));
        this.interactionsList.add(new ItemInteraction("wf_act_execute_for_users", WiredEffectTriggerStacks.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_trg_by_user", WiredConditionTriggererMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_trg_by_user", WiredConditionNotTriggererMatch.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_bot_is_dancing", WiredConditionNotUserPerformsAction.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_bot", WiredTriggerHabboClicksUser.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_double_click_furni", WiredTriggerHabboClicksFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_anti_afk", WiredTriggerHabboUnidles.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_condition_change", WiredExtraOrEval.class));
        this.interactionsList.add(new ItemInteraction("wf_act_send_bubble", WiredEffectMakeUserSay.class));
        this.interactionsList.add(new ItemInteraction("wf_act_double_click", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_enable", WiredEffectGiveEffect.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_forward_user_to_room", WiredEffectForwardUserToRoom.class));
        // Official teleport-to-room wired furni (exist in furnidata) -> same effect (reuses the SHOW_MESSAGE dialog;
        // text field = target room id).
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_to_room", WiredEffectForwardUserToRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_act_tele_room", WiredEffectForwardUserToRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_in_range", WiredConditionUserInRange.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_not_in_range", WiredConditionUserNotInRange.class));
        this.interactionsList.add(
                new ItemInteraction("wf_trg_username_as_trigger", WiredTriggerUsernameAsTrigger.class));
        this.interactionsList.add(new ItemInteraction("wf_act_alert_habbo", WiredEffectAlert.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_talk_custom", WiredEffectBotTalk.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_bot_talk_to_avatar_custom", WiredEffectBotTalkToHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_call_stacks_custom", WiredEffectTriggerStacks.class));
        this.interactionsList.add(new ItemInteraction("wf_act_execute_stack_custom", WiredEffectTriggerStacks.class));
        this.interactionsList.add(new ItemInteraction("wf_act_cnd_move_furni", WiredEffectMoveFurniTo.class));
        this.interactionsList.add(new ItemInteraction("wf_act_cnd_move_rotate", WiredEffectMoveRotateFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_cnd_toggle_state", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_freeze_habbo", WiredEffectFreeze.class));
        this.interactionsList.add(new ItemInteraction("wf_act_unfreeze_habbo", WiredEffectUnfreeze.class));
        this.interactionsList.add(new ItemInteraction("wf_act_match_to_sshot_new", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_furni_to_furni", WiredEffectFurniToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_teleport_bots_to_furni", WiredEffectBotTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_tp_furni_to_habbo", WiredEffectFurniToUser.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_in_group", WiredConditionGroupMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_habbo_in_group", WiredConditionNotInGroup.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_match_snapshot_new", WiredConditionMatchStatePosition.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_match_snap_new", WiredConditionNotMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_tgr_furni_hv_avtrs", WiredConditionFurniHaveHabbo.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_tgr_furni_hv_avtrs", WiredConditionNotFurniHaveHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wears_effect", WiredConditionHabboHasEffect.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_wears_effect", WiredConditionNotHabboHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wears_handitem", WiredConditionHabboHasHandItem.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_wears_handitem", WiredConditionNotHabboHasHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_trgr_stuff_matches", WiredConditionFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_cnd_collision", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_exits_room", WiredTriggerHabboLeavesRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_score_custom", WiredEffectGiveScore.class));
        this.interactionsList.add(new ItemInteraction("wf_act_lower_furni", WiredEffectSetAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_act_raise_furni", WiredEffectSetAltitude.class));
        this.interactionsList.add(new ItemInteraction("wf_act_match_to_sshot_height", WiredEffectMatchFurni.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_match_to_sshot_height_instant", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_plus_match_furni_state", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_rotate_collide", WiredEffectMoveRotateFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_rotate_diagonal", WiredEffectMoveRotateFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_rotate_habbo", WiredEffectMoveRotateUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_show_message_room", WiredEffectWhisper.class));
        this.interactionsList.add(new ItemInteraction("wf_act_toggle_state_down", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_toggle_state_trg", WiredEffectToggleFurni.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_atleast_one_user_in_team", WiredConditionTeamMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_bot_is_dancing", WiredConditionUserPerformsAction.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_habbo_is_dancing", WiredConditionUserPerformsAction.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_is_dancing", WiredConditionNotUserPerformsAction.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_furni_state_pattern", WiredConditionMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_is_state", WiredConditionMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_trg_state_is", WiredConditionMatchStatePosition.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_furnis_hv_avtrs_custom", WiredConditionFurniHaveHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_x_habbos_on_furni", WiredConditionFurniHaveHabbo.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_exact_keyword", WiredTriggerHabboSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_says_command", WiredTriggerHabboSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_habbo_says_command", WiredTriggerHabboSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_other_collides_user", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_collides_bot", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_collides_other", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_one_condition", WiredExtraOrEval.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_furni_as_group", WiredEffectMoveFurniAsGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_remote", WiredEffectRemoteSelector.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_curve", WiredExtraMovementCurve.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_time_util", WiredExtraTimeUtilities.class));
        // ---- end inert-furni group ----

        this.interactionsList.add(new ItemInteraction("wf_highscore", InteractionWiredHighscore.class));

        this.interactionsList.add(new ItemInteraction("battlebanzai_tile", InteractionBattleBanzaiTile.class));
        this.interactionsList.add(
                new ItemInteraction("battlebanzai_random_teleport", InteractionBattleBanzaiTeleporter.class));
        this.interactionsList.add(new ItemInteraction("battlebanzai_sphere", InteractionBattleBanzaiSphere.class));
        this.interactionsList.add(new ItemInteraction("battlebanzai_puck", InteractionBattleBanzaiPuck.class));

        this.interactionsList.add(new ItemInteraction("battlebanzai_gate_blue", InteractionBattleBanzaiGateBlue.class));
        this.interactionsList.add(
                new ItemInteraction("battlebanzai_gate_green", InteractionBattleBanzaiGateGreen.class));
        this.interactionsList.add(new ItemInteraction("battlebanzai_gate_red", InteractionBattleBanzaiGateRed.class));
        this.interactionsList.add(
                new ItemInteraction("battlebanzai_gate_yellow", InteractionBattleBanzaiGateYellow.class));

        this.interactionsList.add(
                new ItemInteraction("battlebanzai_counter_blue", InteractionBattleBanzaiScoreboardBlue.class));
        this.interactionsList.add(
                new ItemInteraction("battlebanzai_counter_green", InteractionBattleBanzaiScoreboardGreen.class));
        this.interactionsList.add(
                new ItemInteraction("battlebanzai_counter_red", InteractionBattleBanzaiScoreboardRed.class));
        this.interactionsList.add(
                new ItemInteraction("battlebanzai_counter_yellow", InteractionBattleBanzaiScoreboardYellow.class));

        this.interactionsList.add(new ItemInteraction("freeze_block", InteractionFreezeBlock.class));
        this.interactionsList.add(new ItemInteraction("freeze_tile", InteractionFreezeTile.class));
        this.interactionsList.add(new ItemInteraction("freeze_exit", InteractionFreezeExitTile.class));

        this.interactionsList.add(new ItemInteraction("freeze_gate_blue", InteractionFreezeGateBlue.class));
        this.interactionsList.add(new ItemInteraction("freeze_gate_green", InteractionFreezeGateGreen.class));
        this.interactionsList.add(new ItemInteraction("freeze_gate_red", InteractionFreezeGateRed.class));
        this.interactionsList.add(new ItemInteraction("freeze_gate_yellow", InteractionFreezeGateYellow.class));

        this.interactionsList.add(new ItemInteraction("freeze_counter_blue", InteractionFreezeScoreboardBlue.class));
        this.interactionsList.add(new ItemInteraction("freeze_counter_green", InteractionFreezeScoreboardGreen.class));
        this.interactionsList.add(new ItemInteraction("freeze_counter_red", InteractionFreezeScoreboardRed.class));
        this.interactionsList.add(
                new ItemInteraction("freeze_counter_yellow", InteractionFreezeScoreboardYellow.class));

        this.interactionsList.add(new ItemInteraction("icetag_pole", InteractionIceTagPole.class));
        this.interactionsList.add(new ItemInteraction("icetag_field", InteractionIceTagField.class));

        this.interactionsList.add(new ItemInteraction("bunnyrun_pole", InteractionBunnyrunPole.class));
        this.interactionsList.add(new ItemInteraction("bunnyrun_field", InteractionBunnyrunField.class));

        this.interactionsList.add(new ItemInteraction("rollerskate_field", InteractionRollerskateField.class));

        this.interactionsList.add(new ItemInteraction("football", InteractionFootball.class));
        this.interactionsList.add(new ItemInteraction("rebug_football", InteractionRebugFootball.class));
        this.interactionsList.add(new ItemInteraction("football_gate", InteractionFootballGate.class));
        this.interactionsList.add(
                new ItemInteraction("football_counter_blue", InteractionFootballScoreboardBlue.class));
        this.interactionsList.add(
                new ItemInteraction("football_counter_green", InteractionFootballScoreboardGreen.class));
        this.interactionsList.add(new ItemInteraction("football_counter_red", InteractionFootballScoreboardRed.class));
        this.interactionsList.add(
                new ItemInteraction("football_counter_yellow", InteractionFootballScoreboardYellow.class));
        this.interactionsList.add(new ItemInteraction("football_goal_blue", InteractionFootballGoalBlue.class));
        this.interactionsList.add(new ItemInteraction("football_goal_green", InteractionFootballGoalGreen.class));
        this.interactionsList.add(new ItemInteraction("football_goal_red", InteractionFootballGoalRed.class));
        this.interactionsList.add(new ItemInteraction("football_goal_yellow", InteractionFootballGoalYellow.class));

        this.interactionsList.add(new ItemInteraction("snowstorm_tree", null));
        this.interactionsList.add(new ItemInteraction("snowstorm_machine", null));
        this.interactionsList.add(new ItemInteraction("snowstorm_pile", null));

        this.interactionsList.add(new ItemInteraction("vote_counter", InteractionVoteCounter.class));

        this.interactionsList.add(new ItemInteraction("totem_leg", InteractionTotemLegs.class));
        this.interactionsList.add(new ItemInteraction("totem_head", InteractionTotemHead.class));
        this.interactionsList.add(new ItemInteraction("totem_planet", InteractionTotemPlanet.class));
    }

    public void addItemInteraction(ItemInteraction itemInteraction) {
        this.interactionsList.addChecked(itemInteraction);
    }

    public ItemInteraction getItemInteraction(Class<? extends HabboItem> type) {
        ItemInteraction interaction = this.interactionsList.find(type);
        if (interaction != null) {
            return interaction;
        }

        LOGGER.debug("Can't find interaction class: {}", type.getName());
        return this.getItemInteraction(InteractionDefault.class);
    }

    public ItemInteraction getItemInteraction(String type) {
        ItemInteraction interaction = this.interactionsList.find(type);
        if (interaction != null) {
            return interaction;
        }

        return this.getItemInteraction(InteractionDefault.class);
    }

    public void loadItems() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(BASE_ITEMS_SQL)) {
            while (set.next()) {
                try {
                    // Item proxyItem =
                    int id = set.getInt("id");
                    if (!this.items.containsKey(id)) this.items.put(id, new Item(set));
                    else this.items.get(id).update(set);
                } catch (Exception e) {
                    LOGGER.error("Failed to load Item ({})", set.getInt("id"));
                    LOGGER.error("Caught exception", e);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void loadCrackable() {
        this.crackableRewards.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM items_crackable");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                CrackableReward reward;
                try {
                    reward = new CrackableReward(set);
                } catch (Exception e) {
                    LOGGER.error("Failed to load items_crackable item_id = {}", set.getInt("item_id"));
                    LOGGER.error("Caught exception", e);
                    continue;
                }
                this.crackableRewards.put(set.getInt("item_id"), reward);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    public int getCrackableCount(int itemId) {
        if (this.crackableRewards.containsKey(itemId)) return this.crackableRewards.get(itemId).count;
        else return 0;
    }

    public int calculateCrackState(int count, int max, Item baseItem) {
        if (count <= 0 || max <= 0 || baseItem == null || baseItem.getStateCount() <= 0) {
            return 0;
        }

        return (int) Math.floor((1.0D / ((double) max / (double) count) * baseItem.getStateCount()));
    }

    public CrackableReward getCrackableData(int itemId) {
        return this.crackableRewards.get(itemId);
    }

    public Item getCrackableReward(int itemId) {
        CrackableReward reward = this.crackableRewards.get(itemId);
        return reward == null ? null : this.getItem(reward.getRandomReward());
    }

    public void loadSoundTracks() {
        this.soundTracks.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM soundtracks");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                this.soundTracks.put(set.getString("code"), new SoundTrack(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public SoundTrack getSoundTrack(String code) {
        return this.soundTracks.get(code);
    }

    public SoundTrack getSoundTrack(int id) {
        for (Map.Entry<String, SoundTrack> entry : this.soundTracks.entrySet()) {
            if (entry.getValue().getId() == id) return entry.getValue();
        }

        return null;
    }

    public void addSoundTrack(SoundTrack track) {
        this.soundTracks.put(track.getCode(), track);
    }

    public void removeSoundTrack(String code) {
        this.soundTracks.remove(code);
    }

    public Item getFirstItemByInteraction(Class<? extends HabboItem> interactionClass) {
        for (Item item : this.items.values()) {
            if (item.getInteractionType() != null && item.getInteractionType().getType() == interactionClass) {
                return item;
            }
        }

        return null;
    }

    public HabboItem createItem(int habboId, Item item, int limitedStack, int limitedSells, String extraData) {
        if (habboId <= 0 || item == null) {
            return null;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            return this.createItem(connection, habboId, item, limitedStack, limitedSells, extraData);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return null;
    }

    public HabboItem createItem(
            Connection connection, int habboId, Item item, int limitedStack, int limitedSells, String extraData)
            throws SQLException {
        if (habboId <= 0 || item == null) {
            return null;
        }

        extraData = ItemDataGuard.normalizeExtraData(extraData);

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items (user_id, item_id, extra_data, limited_data) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, habboId);
            statement.setInt(2, item.getId());
            statement.setString(3, extraData);
            statement.setString(4, limitedStack + ":" + limitedSells);
            statement.execute();

            try (ResultSet set = statement.getGeneratedKeys()) {
                if (set.next()) {
                    Class<? extends HabboItem> itemClass =
                            item.getInteractionType().getType();

                    if (itemClass != null) {
                        try {
                            return itemClass
                                    .getDeclaredConstructor(
                                            int.class, int.class, Item.class, String.class, int.class, int.class)
                                    .newInstance(set.getInt(1), habboId, item, extraData, limitedStack, limitedSells);
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                            return new InteractionDefault(
                                    set.getInt(1), habboId, item, extraData, limitedStack, limitedSells);
                        }
                    }
                }
            }
        }
        return null;
    }

    public void loadNewUserGifts() {
        this.newuserGifts.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM nux_gifts")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.newuserGifts.put(set.getInt("id"), new NewUserGift(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void addNewUserGift(NewUserGift gift) {
        this.newuserGifts.put(gift.getId(), gift);
    }

    public void removeNewUserGift(NewUserGift gift) {
        this.newuserGifts.remove(gift.getId());
    }

    public NewUserGift getNewUserGift(int id) {
        return this.newuserGifts.get(id);
    }

    public List<NewUserGift> getNewUserGifts() {
        return new ArrayList<>(this.newuserGifts.values());
    }

    public void deleteItem(HabboItem item) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM items_teleports WHERE teleport_one_id = ? OR teleport_two_id = ?")) {
                statement.setInt(1, item.getId());
                statement.setInt(2, item.getId());
                statement.executeUpdate();
            }
            try (PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM items_hoppers WHERE item_id = ?")) {
                statement.setInt(1, item.getId());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
                statement.setInt(1, item.getId());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public HabboItem handleRecycle(Habbo habbo, String itemId) {
        int rewardItemId = ItemDataGuard.parsePositiveInt(itemId);
        if (habbo == null
                || habbo.getHabboInfo() == null
                || rewardItemId <= 0
                || Emulator.getGameEnvironment().getCatalogManager().ecotronItem == null) {
            return null;
        }

        String extradata = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "-"
                + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "-"
                + Calendar.getInstance().get(Calendar.YEAR);

        HabboItem item = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO items (user_id, item_id, extra_data) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(
                    2,
                    Emulator.getGameEnvironment()
                            .getCatalogManager()
                            .ecotronItem
                            .getId());
            statement.setString(3, extradata);
            statement.execute();

            try (ResultSet set = statement.getGeneratedKeys()) {
                try (PreparedStatement preparedStatement = connection.prepareStatement(
                        "INSERT INTO items_presents (item_id, base_item_reward) VALUES (?, ?)")) {
                    while (set.next() && item == null) {
                        preparedStatement.setInt(1, set.getInt(1));
                        preparedStatement.setInt(2, rewardItemId);
                        preparedStatement.addBatch();
                        item = new InteractionDefault(
                                set.getInt(1),
                                habbo.getHabboInfo().getId(),
                                Emulator.getGameEnvironment().getCatalogManager().ecotronItem,
                                extradata,
                                0,
                                0);
                    }

                    preparedStatement.executeBatch();
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return item;
    }

    public HabboItem handleOpenRecycleBox(Habbo habbo, HabboItem box) {
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(box.getId()));
        HabboItem item = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM items_presents WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, box.getId());
            try (ResultSet rewardSet = statement.executeQuery()) {
                if (rewardSet.next()) {
                    try (PreparedStatement preparedStatement = connection.prepareStatement(
                            "INSERT INTO items (user_id, item_id) VALUES(?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        preparedStatement.setInt(1, habbo.getHabboInfo().getId());
                        preparedStatement.setInt(2, rewardSet.getInt("base_item_reward"));
                        preparedStatement.execute();

                        try (ResultSet set = preparedStatement.getGeneratedKeys()) {
                            if (set.next()) {
                                try (PreparedStatement request =
                                        connection.prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
                                    request.setInt(1, set.getInt(1));

                                    try (ResultSet resultSet = request.executeQuery()) {
                                        if (resultSet.next()) {
                                            try (PreparedStatement deleteStatement = connection.prepareStatement(
                                                    "DELETE FROM items_presents WHERE item_id = ? LIMIT 1")) {
                                                deleteStatement.setInt(1, box.getId());
                                                deleteStatement.execute();

                                                item = this.loadHabboItem(resultSet);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return item;
    }

    public void insertTeleportPair(int itemOneId, int itemTwoId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            this.insertTeleportPair(connection, itemOneId, itemTwoId);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void insertTeleportPair(Connection connection, int itemOneId, int itemTwoId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items_teleports (teleport_one_id, teleport_two_id) VALUES (?, ?)")) {
            statement.setInt(1, itemOneId);
            statement.setInt(2, itemTwoId);
            statement.execute();
        }
    }

    public void insertHopper(HabboItem hopper) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            this.insertHopper(connection, hopper);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void insertHopper(Connection connection, HabboItem hopper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO items_hoppers VALUES (?, ?)")) {
            statement.setInt(1, hopper.getId());
            statement.setInt(2, hopper.getBaseItem().getId());
            statement.execute();
        }
    }

    public int[] getTargetTeleportRoomId(HabboItem item) {
        int[] target = new int[] {};

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT items_teleports.*, A.room_id as a_room_id, A.id as a_id, B.room_id as b_room_id, B.id as b_id FROM items_teleports INNER JOIN items AS A ON items_teleports.teleport_one_id = A.id INNER JOIN items AS B ON items_teleports.teleport_two_id = B.id WHERE (teleport_one_id = ? OR teleport_two_id = ?) LIMIT 1")) {
            statement.setInt(1, item.getId());
            statement.setInt(2, item.getId());

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    final boolean useA = (set.getInt("a_id") != item.getId());
                    final int targetRoomId = useA ? set.getInt("a_room_id") : set.getInt("b_room_id");
                    final int targetItemId = useA ? set.getInt("a_id") : set.getInt("b_id");

                    if (targetRoomId > 0 && targetItemId > 0) {
                        target = new int[] {targetRoomId, targetItemId};
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return target;
    }

    public HabboItem loadHabboItem(int itemId) {
        HabboItem item = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    item = this.loadHabboItem(set);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return item;
    }

    public HabboItem loadHabboItem(ResultSet set) throws SQLException {
        Item baseItem = this.getItem(set.getInt("item_id"));

        if (baseItem == null) return null;

        Class<? extends HabboItem> itemClass = baseItem.getInteractionType().getType();

        if (itemClass != null) {
            try {
                Constructor<?> c = itemClass.getConstructor(ResultSet.class, Item.class);
                c.setAccessible(true);

                return (HabboItem) c.newInstance(set, baseItem);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        return null;
    }

    public HabboItem createGift(String username, Item item, String extraData, int limitedStack, int limitedSells) {
        if (username == null || username.isBlank() || item == null) {
            return null;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);

        int userId = 0;

        if (habbo != null) {
            userId = habbo.getHabboInfo().getId();
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                statement.setString(1, username);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        userId = set.getInt(1);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        if (userId > 0) {
            return createGift(userId, item, extraData, limitedStack, limitedSells);
        }

        return null;
    }

    public HabboItem createGift(int userId, Item item, String extraData, int limitedStack, int limitedSells) {
        if (userId <= 0 || item == null) return null;

        if (extraData != null && extraData.length() > ItemDataGuard.MAX_EXTRA_DATA_LENGTH) {
            LOGGER.error("Extradata exceeds maximum length of 1000 characters: {}", extraData);
        }
        extraData = ItemDataGuard.normalizeExtraData(extraData);

        HabboItem gift = this.createItem(userId, item, limitedStack, limitedSells, extraData);

        if (gift != null) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo != null) {
                habbo.getInventory().getItemsComponent().addItem(gift);
                habbo.getClient().sendResponse(new AddHabboItemComposer(gift));
            }
        }

        return gift;
    }

    public Item getItem(int itemId) {
        if (itemId <= 0) return null;

        return this.items.get(itemId);
    }

    public Int2ObjectMap<Item> getItems() {
        return this.items;
    }

    public Item getItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        synchronized (this.items) {
            for (Item item : this.items.values()) {
                if (item != null && item.getName() != null && item.getName().equalsIgnoreCase(itemName)) {
                    return item;
                }
            }
        }

        return null;
    }

    public YoutubeManager getYoutubeManager() {
        return this.youtubeManager;
    }

    public WiredHighscoreManager getHighscoreManager() {
        return highscoreManager;
    }

    public void dispose() {
        this.items.clear();
        this.highscoreManager.dispose();

        LOGGER.info("Item Manager -> Disposed!");
    }

    public List<String> getInteractionList() {
        return this.interactionsList.sortedNames();
    }
}
