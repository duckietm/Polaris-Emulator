package polaris.hotel.items;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionAreaHideControl;
import polaris.hotel.items.interactions.InteractionArrow;
import polaris.hotel.items.interactions.InteractionBackgroundToner;
import polaris.hotel.items.interactions.InteractionBadgeDisplay;
import polaris.hotel.items.interactions.InteractionBlackHole;
import polaris.hotel.items.interactions.InteractionBuildArea;
import polaris.hotel.items.interactions.InteractionCannon;
import polaris.hotel.items.interactions.InteractionChooserDisableControl;
import polaris.hotel.items.interactions.InteractionClothing;
import polaris.hotel.items.interactions.InteractionClubGate;
import polaris.hotel.items.interactions.InteractionClubHopper;
import polaris.hotel.items.interactions.InteractionClubTeleportTile;
import polaris.hotel.items.interactions.InteractionColorPlate;
import polaris.hotel.items.interactions.InteractionColorWheel;
import polaris.hotel.items.interactions.InteractionConfInvisControl;
import polaris.hotel.items.interactions.InteractionCostumeHopper;
import polaris.hotel.items.interactions.InteractionCrackable;
import polaris.hotel.items.interactions.InteractionCrackableMaster;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.items.interactions.InteractionDice;
import polaris.hotel.items.interactions.InteractionDiceDisableControl;
import polaris.hotel.items.interactions.InteractionDoorkickDisableControl;
import polaris.hotel.items.interactions.InteractionEffectGate;
import polaris.hotel.items.interactions.InteractionEffectGiver;
import polaris.hotel.items.interactions.InteractionEffectTile;
import polaris.hotel.items.interactions.InteractionEffectToggle;
import polaris.hotel.items.interactions.InteractionEffectVendingMachine;
import polaris.hotel.items.interactions.InteractionEffectVendingMachineNoSides;
import polaris.hotel.items.interactions.InteractionExternalImage;
import polaris.hotel.items.interactions.InteractionFXBox;
import polaris.hotel.items.interactions.InteractionFireworks;
import polaris.hotel.items.interactions.InteractionGate;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.items.interactions.InteractionGroupPressurePlate;
import polaris.hotel.items.interactions.InteractionGuildFurni;
import polaris.hotel.items.interactions.InteractionGuildGate;
import polaris.hotel.items.interactions.InteractionGymEquipment;
import polaris.hotel.items.interactions.InteractionHanditem;
import polaris.hotel.items.interactions.InteractionHanditemBlockControl;
import polaris.hotel.items.interactions.InteractionHanditemTile;
import polaris.hotel.items.interactions.InteractionHideWiredControl;
import polaris.hotel.items.interactions.InteractionHopper;
import polaris.hotel.items.interactions.InteractionInformationTerminal;
import polaris.hotel.items.interactions.InteractionJukeBox;
import polaris.hotel.items.interactions.InteractionLoveLock;
import polaris.hotel.items.interactions.InteractionMannequin;
import polaris.hotel.items.interactions.InteractionMonsterCrackable;
import polaris.hotel.items.interactions.InteractionMoodLight;
import polaris.hotel.items.interactions.InteractionMultiHeight;
import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.hotel.items.interactions.InteractionMuteArea;
import polaris.hotel.items.interactions.InteractionNoSidesVendingMachine;
import polaris.hotel.items.interactions.InteractionObstacle;
import polaris.hotel.items.interactions.InteractionOneWayGate;
import polaris.hotel.items.interactions.InteractionPlant;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.items.interactions.InteractionPressurePlate;
import polaris.hotel.items.interactions.InteractionPuzzleBox;
import polaris.hotel.items.interactions.InteractionPyramid;
import polaris.hotel.items.interactions.InteractionQueueSpeedControl;
import polaris.hotel.items.interactions.InteractionRandomState;
import polaris.hotel.items.interactions.InteractionRecolorable;
import polaris.hotel.items.interactions.InteractionRedeemableSubscriptionBox;
import polaris.hotel.items.interactions.InteractionRentableSpace;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.items.interactions.InteractionRoomAds;
import polaris.hotel.items.interactions.InteractionRoomOMatic;
import polaris.hotel.items.interactions.InteractionSnowboardSlope;
import polaris.hotel.items.interactions.InteractionStackHelper;
import polaris.hotel.items.interactions.InteractionStackWalkHelper;
import polaris.hotel.items.interactions.InteractionStepLink;
import polaris.hotel.items.interactions.InteractionStickyPole;
import polaris.hotel.items.interactions.InteractionSwitch;
import polaris.hotel.items.interactions.InteractionSwitchRemoteControl;
import polaris.hotel.items.interactions.InteractionTalkingFurniture;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionTeleportTile;
import polaris.hotel.items.interactions.InteractionTent;
import polaris.hotel.items.interactions.InteractionTileEffectProvider;
import polaris.hotel.items.interactions.InteractionTileWalkMagic;
import polaris.hotel.items.interactions.InteractionTrap;
import polaris.hotel.items.interactions.InteractionTrophy;
import polaris.hotel.items.interactions.InteractionVendingMachine;
import polaris.hotel.items.interactions.InteractionVikingCotie;
import polaris.hotel.items.interactions.InteractionVimeo;
import polaris.hotel.items.interactions.InteractionVoteCounter;
import polaris.hotel.items.interactions.InteractionWater;
import polaris.hotel.items.interactions.InteractionWaterCan;
import polaris.hotel.items.interactions.InteractionWaterItem;
import polaris.hotel.items.interactions.InteractionWiredDisableControl;
import polaris.hotel.items.interactions.InteractionWiredHighscore;
import polaris.hotel.items.interactions.InteractionWiredRoomLinker;
import polaris.hotel.items.interactions.InteractionYoutubeTV;
import polaris.hotel.items.interactions.games.InteractionGameTimer;
import polaris.hotel.items.interactions.games.InteractionGameUpCounter;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiPuck;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTile;
import polaris.hotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateBlue;
import polaris.hotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateGreen;
import polaris.hotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateRed;
import polaris.hotel.items.interactions.games.battlebanzai.gates.InteractionBattleBanzaiGateYellow;
import polaris.hotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardBlue;
import polaris.hotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardGreen;
import polaris.hotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardRed;
import polaris.hotel.items.interactions.games.battlebanzai.scoreboards.InteractionBattleBanzaiScoreboardYellow;
import polaris.hotel.items.interactions.games.football.InteractionFootball;
import polaris.hotel.items.interactions.games.football.InteractionFootballGate;
import polaris.hotel.items.interactions.games.football.InteractionRebugFootball;
import polaris.hotel.items.interactions.games.football.goals.InteractionFootballGoalBlue;
import polaris.hotel.items.interactions.games.football.goals.InteractionFootballGoalGreen;
import polaris.hotel.items.interactions.games.football.goals.InteractionFootballGoalRed;
import polaris.hotel.items.interactions.games.football.goals.InteractionFootballGoalYellow;
import polaris.hotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardBlue;
import polaris.hotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardGreen;
import polaris.hotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardRed;
import polaris.hotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboardYellow;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeBlock;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeTile;
import polaris.hotel.items.interactions.games.freeze.gates.InteractionFreezeGateBlue;
import polaris.hotel.items.interactions.games.freeze.gates.InteractionFreezeGateGreen;
import polaris.hotel.items.interactions.games.freeze.gates.InteractionFreezeGateRed;
import polaris.hotel.items.interactions.games.freeze.gates.InteractionFreezeGateYellow;
import polaris.hotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardBlue;
import polaris.hotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardGreen;
import polaris.hotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardRed;
import polaris.hotel.items.interactions.games.freeze.scoreboards.InteractionFreezeScoreboardYellow;
import polaris.hotel.items.interactions.games.tag.bunnyrun.InteractionBunnyrunField;
import polaris.hotel.items.interactions.games.tag.bunnyrun.InteractionBunnyrunPole;
import polaris.hotel.items.interactions.games.tag.icetag.InteractionIceTagField;
import polaris.hotel.items.interactions.games.tag.icetag.InteractionIceTagPole;
import polaris.hotel.items.interactions.games.tag.rollerskate.InteractionRollerskateField;
import polaris.hotel.items.interactions.mysterybox.InteractionMysteryBox;
import polaris.hotel.items.interactions.mysterybox.InteractionMysteryBoxKey;
import polaris.hotel.items.interactions.pets.InteractionMonsterPlantSeed;
import polaris.hotel.items.interactions.pets.InteractionNest;
import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.items.interactions.pets.InteractionPetDrink;
import polaris.hotel.items.interactions.pets.InteractionPetFood;
import polaris.hotel.items.interactions.pets.InteractionPetToy;
import polaris.hotel.items.interactions.pets.InteractionPetTrampoline;
import polaris.hotel.items.interactions.pets.InteractionPetTree;
import polaris.hotel.items.interactions.totems.InteractionTotemHead;
import polaris.hotel.items.interactions.totems.InteractionTotemLegs;
import polaris.hotel.items.interactions.totems.InteractionTotemPlanet;
import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.items.interactions.wired.chest.ChestRelease;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionActorDir;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionBattleBanzaiRunning;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionCheckArray;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionChestHasItemType;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionChestHasItems;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionCounterTimeMatches;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionDateRangeActive;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionFrozen;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionFurniHaveFurni;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionFurniHaveUser;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionFurniInRange;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionFurniNotInRange;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionFurniOpacityIs;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionFurniTypeMatch;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionGroupMember;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionHasAltitude;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionHasTag;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionHasVariable;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionLessTimeElapsed;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionMatchDate;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionMatchStatePosition;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionMatchTime;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionMoreTimeElapsed;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionMottoContains;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionMovementValidation;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNoBattleBanzaiRunning;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotFrozen;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotFurniHaveFurni;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotFurniHaveUser;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotFurniOpacityIs;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotFurniTypeMatch;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotHasTag;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotHasVariable;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotInGroup;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotInTeam;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotMatchStatePosition;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotSameHeight;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotTriggerOnFurni;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotTriggererMatch;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserCount;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserHasEffect;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserHasHandItem;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserHasHighscorePoints;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserHasRank;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserOwnsBadge;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserPerformsAction;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionNotUserWearsBadge;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionSameHeight;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionSelectionQuantity;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionTeamHasRank;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionTeamHasScore;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionTeamMember;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionTriggerFurniAdjacentState;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionTriggerOnFurni;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionTriggererMatch;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserCooldown;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserCount;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserDaily;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserFirstTime;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasCredits;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasDuckets;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasEffect;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasHandItem;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasHighscorePoints;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasMinItems;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasRank;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserHasRights;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserInRange;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserIsFemale;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserIsMale;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserLacksCredits;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserLacksDiamonds;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserLacksDuckets;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserLevel;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserNotHasRights;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserNotInRange;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserNotOwnsFurni;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserOnFurniWithState;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserOwnsBadge;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserOwnsFurni;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserPerformsAction;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionUserWearsBadge;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionVariableAgeMatch;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionVariableValueMatch;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredContractPayment;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredContractReward;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredContractTrade;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredCustomContract;
import polaris.hotel.items.interactions.wired.effects.WiredEffectAddTag;
import polaris.hotel.items.interactions.wired.effects.WiredEffectAdjustClock;
import polaris.hotel.items.interactions.wired.effects.WiredEffectAlert;
import polaris.hotel.items.interactions.wired.effects.WiredEffectAllUsersLeaveTeam;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotClothes;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotDance;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotFollowUser;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotGiveHandItem;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotTalk;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotTalkToUser;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotTeleport;
import polaris.hotel.items.interactions.wired.effects.WiredEffectBotWalkToFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectCancelTransaction;
import polaris.hotel.items.interactions.wired.effects.WiredEffectChangeFurniDirection;
import polaris.hotel.items.interactions.wired.effects.WiredEffectChangeOpacity;
import polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import polaris.hotel.items.interactions.wired.effects.WiredEffectClickSettings;
import polaris.hotel.items.interactions.wired.effects.WiredEffectControlClock;
import polaris.hotel.items.interactions.wired.effects.WiredEffectForwardUserToRoom;
import polaris.hotel.items.interactions.wired.effects.WiredEffectFreeze;
import polaris.hotel.items.interactions.wired.effects.WiredEffectFurniToFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectFurniToUser;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveAchievement;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveBadge;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveCredits;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveCurrencyFromChest;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveDiamonds;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveDuckets;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveEffect;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveExperience;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveFurniFromChest;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveHandItem;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveHotelviewBonusRarePoints;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveHotelviewHofPoints;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveLook;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveOrTakeFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGivePointsHighscore;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGivePointsType;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveRespect;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveReward;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveScore;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveScoreToTeam;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import polaris.hotel.items.interactions.wired.effects.WiredEffectInitTransaction;
import polaris.hotel.items.interactions.wired.effects.WiredEffectJoinTeam;
import polaris.hotel.items.interactions.wired.effects.WiredEffectKickUser;
import polaris.hotel.items.interactions.wired.effects.WiredEffectLay;
import polaris.hotel.items.interactions.wired.effects.WiredEffectLeaveTeam;
import polaris.hotel.items.interactions.wired.effects.WiredEffectLog;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMakeFastWalk;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMakeUserSay;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectModifyArray;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveAsGroup;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveFurniAsGroup;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveFurniAway;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveFurniTo;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveFurniTowards;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveRotateFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveRotateUser;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveUserTiles;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMuteUser;
import polaris.hotel.items.interactions.wired.effects.WiredEffectNegativeLog;
import polaris.hotel.items.interactions.wired.effects.WiredEffectNegativeSendSignal;
import polaris.hotel.items.interactions.wired.effects.WiredEffectNegativeShowMessage;
import polaris.hotel.items.interactions.wired.effects.WiredEffectNegativeTriggerStacks;
import polaris.hotel.items.interactions.wired.effects.WiredEffectOpenUserPages;
import polaris.hotel.items.interactions.wired.effects.WiredEffectOverrideHeight;
import polaris.hotel.items.interactions.wired.effects.WiredEffectPlaceFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectPlaceTemporaryFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectPlayYoutube;
import polaris.hotel.items.interactions.wired.effects.WiredEffectProgressAchievement;
import polaris.hotel.items.interactions.wired.effects.WiredEffectProgressRewardTrack;
import polaris.hotel.items.interactions.wired.effects.WiredEffectQuickBopper;
import polaris.hotel.items.interactions.wired.effects.WiredEffectRelativeMove;
import polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveBadge;
import polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveTag;
import polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveTemporaryFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import polaris.hotel.items.interactions.wired.effects.WiredEffectResetHighscores;
import polaris.hotel.items.interactions.wired.effects.WiredEffectResetRewardTrack;
import polaris.hotel.items.interactions.wired.effects.WiredEffectResetTimers;
import polaris.hotel.items.interactions.wired.effects.WiredEffectRollDice;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSayCommand;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSendLinkDataframe;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSendLinkMessage;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSendSignal;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSetAltitude;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSetHighlight;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSetRollerSpeed;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSetRoomAd;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSit;
import polaris.hotel.items.interactions.wired.effects.WiredEffectTeleport;
import polaris.hotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectToggleMoodlight;
import polaris.hotel.items.interactions.wired.effects.WiredEffectToggleRandom;
import polaris.hotel.items.interactions.wired.effects.WiredEffectTriggerStacks;
import polaris.hotel.items.interactions.wired.effects.WiredEffectUnfreeze;
import polaris.hotel.items.interactions.wired.effects.WiredEffectUserToFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectWalkToFurni;
import polaris.hotel.items.interactions.wired.effects.WiredEffectWhisper;
import polaris.hotel.items.interactions.wired.extra.WiredBlob;
import polaris.hotel.items.interactions.wired.extra.WiredExtraAchievementEnabler;
import polaris.hotel.items.interactions.wired.extra.WiredExtraAnimationTime;
import polaris.hotel.items.interactions.wired.extra.WiredExtraArrayCaptureVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraChestItemTypeScanner;
import polaris.hotel.items.interactions.wired.extra.WiredExtraContextVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraDailyTask;
import polaris.hotel.items.interactions.wired.extra.WiredExtraExecuteInOrder;
import polaris.hotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFilterFurniByVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFilterUser;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFilterUsersByVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.hotel.items.interactions.wired.extra.WiredExtraLinkSpeaker;
import polaris.hotel.items.interactions.wired.extra.WiredExtraMoveCarryUsers;
import polaris.hotel.items.interactions.wired.extra.WiredExtraMoveNoAnimation;
import polaris.hotel.items.interactions.wired.extra.WiredExtraMovePhysics;
import polaris.hotel.items.interactions.wired.extra.WiredExtraMovementCurve;
import polaris.hotel.items.interactions.wired.extra.WiredExtraOrEval;
import polaris.hotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraProjectile;
import polaris.hotel.items.interactions.wired.extra.WiredExtraQuest;
import polaris.hotel.items.interactions.wired.extra.WiredExtraQuestChain;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRandom;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextInputVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputFurniName;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputGlobal;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputUsername;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUnseen;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableAddonImport;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableAddonShare;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFxBossBar;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFxHealthPoints;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFxLevellingProgress;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFxNumberDisplay;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFxProgressBar;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFxStatusBar;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableLeaderboard;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableReference;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniAltitude;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniArea;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniByType;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniNeighborhood;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniOnFurni;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniPicks;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniSignal;
import polaris.hotel.items.interactions.wired.selector.WiredEffectFurniWithVariable;
import polaris.hotel.items.interactions.wired.selector.WiredEffectRemoteSelector;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersArea;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersByAction;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersByName;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersByType;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersGroup;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersHandItem;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersNeighborhood;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersOnFurni;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersSignal;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersTeam;
import polaris.hotel.items.interactions.wired.selector.WiredEffectUsersWithVariable;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerAtSetTime;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerAtTimeLong;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerBotReachedFurni;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerBotReachedUser;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerClockCounter;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerCollision;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerDiceRolled;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerFurniStateToggled;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerFurniStateUpdated;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerGameEnds;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerGameStarts;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerPressKeybind;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerReceiveLinkDataframe;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerReceiveLinkMessage;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerRepeater;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerRepeaterLong;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerRepeaterShort;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerRoomInitialized;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerScoreAchieved;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerTeamLoses;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerTeamWins;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerTransactionComplete;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerTransactionFail;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserClicksFurni;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserClicksTile;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserClicksUser;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserEntersRoom;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserGetsHandItem;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserIdles;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserLeavesRoom;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserPerformsAction;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserSaysKeyword;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserStartsDancing;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserStopsDancing;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserUnidles;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserWalkOffFurni;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserWalkOnFurni;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUsernameAsTrigger;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.highscores.WiredHighscoreManager;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.plugin.events.emulator.EmulatorLoadItemsManagerEvent;

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

    public static volatile boolean RECYCLER_ENABLED = true;

    private final Int2ObjectMap<Item> items;
    // Rebuilt whole by their loaders and swapped in: a reload used to empty them while rooms and jukeboxes read them.
    private volatile Int2ObjectMap<CrackableReward> crackableRewards;
    private final ItemInteractionRegistry interactionsList;
    private volatile Map<String, SoundTrack> soundTracks;
    private final YoutubeManager youtubeManager;
    private final WiredHighscoreManager highscoreManager;
    private final TreeMap<Integer, NewUserGift> newuserGifts;
    private final Map<String, PlantConfig> plantConfigs;
    private final Map<Integer, PlantData> plantData;

    public ItemManager() {
        this.items = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        this.crackableRewards = new Int2ObjectOpenHashMap<>();
        this.interactionsList = new ItemInteractionRegistry();
        this.soundTracks = new ConcurrentHashMap<>();
        this.youtubeManager = new YoutubeManager();
        this.highscoreManager = new WiredHighscoreManager();
        this.newuserGifts = new TreeMap<>();
        this.plantConfigs = new ConcurrentHashMap<>();
        this.plantData = new ConcurrentHashMap<>();
    }

    public void load() {
        Emulator.getPluginManager().fireEvent(new EmulatorLoadItemsManagerEvent());

        long millis = System.currentTimeMillis();

        this.loadItemInteractions();
        this.loadItems();
        this.loadCrackable();
        this.loadPlants();
        this.loadSoundTracks();
        this.youtubeManager.load();
        this.highscoreManager.load();
        this.loadNewUserGifts();

        LOGGER.info("Item Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    protected void loadItemInteractions() {
        this.interactionsList.add(new ItemInteraction("default", InteractionDefault.class));
        this.interactionsList.add(new ItemInteraction("water_can", InteractionWaterCan.class));
        this.interactionsList.add(new ItemInteraction("plants", InteractionPlant.class));
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
        this.interactionsList.add(new ItemInteraction("arrow", InteractionArrow.class));
        this.interactionsList.add(new ItemInteraction("step_link", InteractionStepLink.class));
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
        this.interactionsList.add(new ItemInteraction("mystery_box", InteractionMysteryBox.class));
        this.interactionsList.add(new ItemInteraction("mystery_key", InteractionMysteryBoxKey.class));
        this.interactionsList.add(new ItemInteraction("stack_helper", InteractionStackHelper.class));
        this.interactionsList.add(new ItemInteraction("stack_walk_helper", InteractionStackWalkHelper.class));
        this.interactionsList.add(new ItemInteraction("puzzle_box", InteractionPuzzleBox.class));
        this.interactionsList.add(new ItemInteraction("hopper", InteractionHopper.class));
        this.interactionsList.add(new ItemInteraction("costume_hopper", InteractionCostumeHopper.class));
        this.interactionsList.add(new ItemInteraction("effect_gate", InteractionEffectGate.class));
        this.interactionsList.add(new ItemInteraction("club_hopper", InteractionClubHopper.class));
        this.interactionsList.add(new ItemInteraction("club_gate", InteractionClubGate.class));
        this.interactionsList.add(new ItemInteraction("club_teleporttile", InteractionClubTeleportTile.class));
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
        this.interactionsList.add(new ItemInteraction("vimeo", InteractionVimeo.class));
        this.interactionsList.add(new ItemInteraction("jukebox", InteractionJukeBox.class));
        this.interactionsList.add(new ItemInteraction("switch", InteractionSwitch.class));
        this.interactionsList.add(new ItemInteraction("conf_invis_control", InteractionConfInvisControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_invis_control", InteractionConfInvisControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_area_hide", InteractionAreaHideControl.class));
        this.interactionsList.add(new ItemInteraction("conf_area_hide", InteractionAreaHideControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_handitem_block", InteractionHanditemBlockControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_queue_speed", InteractionQueueSpeedControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_wired_disable", InteractionWiredDisableControl.class));
        // The bare classnames, as the other conf_* controllers already accept.
        this.interactionsList.add(new ItemInteraction("conf_handitem_block", InteractionHanditemBlockControl.class));
        this.interactionsList.add(new ItemInteraction("conf_queue_speed", InteractionQueueSpeedControl.class));
        this.interactionsList.add(new ItemInteraction("conf_wired_disable", InteractionWiredDisableControl.class));
        this.interactionsList.add(new ItemInteraction("conf_hidewired", InteractionHideWiredControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_hidewired", InteractionHideWiredControl.class));
        this.interactionsList.add(new ItemInteraction("conf_dice_disable", InteractionDiceDisableControl.class));
        this.interactionsList.add(new ItemInteraction("wf_conf_dice_disable", InteractionDiceDisableControl.class));
        this.interactionsList.add(new ItemInteraction("conf_chooser_disable", InteractionChooserDisableControl.class));
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

        this.interactionsList.add(new ItemInteraction("wf_trg_walks_on_furni", WiredTriggerUserWalkOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_walks_off_furni", WiredTriggerUserWalkOffFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_furni", WiredTriggerUserClicksFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_tile", WiredTriggerUserClicksTile.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_click_user", WiredTriggerUserClicksUser.class));
        this.interactionsList.add(
                new ItemInteraction("wf_trg_user_performs_action", WiredTriggerUserPerformsAction.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_enter_room", WiredTriggerUserEntersRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_leave_room", WiredTriggerUserLeavesRoom.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_says_something", WiredTriggerUserSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_clock_counter", WiredTriggerClockCounter.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_var_changed", WiredTriggerVariableChanged.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_periodically", WiredTriggerRepeater.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_period_short", WiredTriggerRepeaterShort.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_period_long", WiredTriggerRepeaterLong.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_state_changed", WiredTriggerFurniStateUpdated.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_stuff_state", WiredTriggerFurniStateToggled.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_at_given_time", WiredTriggerAtSetTime.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_at_time_long", WiredTriggerAtTimeLong.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_collision", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_starts", WiredTriggerGameStarts.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_ends", WiredTriggerGameEnds.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_bot_reached_stf", WiredTriggerBotReachedFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_bot_reached_avtr", WiredTriggerBotReachedUser.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_score_achieved", WiredTriggerScoreAchieved.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_team_win", WiredTriggerTeamWins.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_game_team_lose", WiredTriggerTeamLoses.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_recv_signal", WiredTriggerReceiveSignal.class));
        // Phase-C triggers (dance/idle)
        this.interactionsList.add(new ItemInteraction("wf_trg_starts_dancing", WiredTriggerUserStartsDancing.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_stops_dancing", WiredTriggerUserStopsDancing.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_idles", WiredTriggerUserIdles.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_unidles", WiredTriggerUserUnidles.class));
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
        this.interactionsList.add(new ItemInteraction("wf_act_open_habbo_pages", WiredEffectOpenUserPages.class));
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
                new ItemInteraction("wf_cnd_not_habbo_has_credits", WiredConditionUserLacksCredits.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_has_diamonds", WiredConditionUserLacksDiamonds.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_has_duckets", WiredConditionUserLacksDuckets.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_freeze", WiredConditionFrozen.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_freeze", WiredConditionNotFrozen.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_furni_in_range", WiredConditionFurniInRange.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_furni_not_in_range", WiredConditionFurniNotInRange.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_same_height", WiredConditionSameHeight.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_has_same_height", WiredConditionNotSameHeight.class));
        // owns_furni: check the triggerer's inventory for the picked furni type(s) (reuse HAS_ALTITUDE picker).
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_owns_furni", WiredConditionUserOwnsFurni.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_habbo_not_owns_furni", WiredConditionUserNotOwnsFurni.class));
        // Negative-branch effects (run when the stack's conditions FAIL); reuse the SHOW_MESSAGE dialog.
        this.interactionsList.add(new ItemInteraction("wf_act_neg_show_message", WiredEffectNegativeShowMessage.class));
        this.interactionsList.add(new ItemInteraction("wf_act_neg_log", WiredEffectNegativeLog.class));
        // give_look exists client-side (FurnitureData): set the user's figure (text dialog = figure string).
        this.interactionsList.add(new ItemInteraction("wf_act_give_look", WiredEffectGiveLook.class));
        // Profile tags (text dialog = the tag; persisted via the new UserStats tag helpers).
        this.interactionsList.add(new ItemInteraction("wf_act_add_tag", WiredEffectAddTag.class));
        this.interactionsList.add(new ItemInteraction("wf_act_add_tag_perm", WiredEffectAddTag.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_tag", WiredEffectRemoveTag.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_tag", WiredConditionHasTag.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_has_tag", WiredConditionNotHasTag.class));
        // Identity conditions reusing a meaningful existing dialog field (text = motto / int = item count).
        this.interactionsList.add(new ItemInteraction("wf_cnd_motto_contains", WiredConditionMottoContains.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_habbo_has_at_least_x_items", WiredConditionUserHasMinItems.class));
        // OWNED-badge check (Phase A skipped these because only a worn-badge class existed): reuses the
        // wears-badge dialog but checks inventory ownership via BadgesComponent.hasBadge.
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_owns_badge", WiredConditionUserOwnsBadge.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_owns_badge", WiredConditionNotUserOwnsBadge.class));

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
        this.interactionsList.add(new ItemInteraction("wf_act_kick_user", WiredEffectKickUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_mute_triggerer", WiredEffectMuteUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_click_conf", WiredEffectClickSettings.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_teleport", WiredEffectBotTeleport.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_move", WiredEffectBotWalkToFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_talk", WiredEffectBotTalk.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_give_handitem", WiredEffectBotGiveHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_follow_avatar", WiredEffectBotFollowUser.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_clothes", WiredEffectBotClothes.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_talk_to_avatar", WiredEffectBotTalkToUser.class));
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
        this.interactionsList.add(new ItemInteraction("wf_act_modify_array", WiredEffectModifyArray.class));

        this.interactionsList.add(new ItemInteraction("wf_cnd_has_furni_on", WiredConditionFurniHaveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_furnis_hv_avtrs", WiredConditionFurniHaveUser.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_stuff_is", WiredConditionFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_in_group", WiredConditionGroupMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_count_in", WiredConditionUserCount.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wearing_effect", WiredConditionUserHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wearing_badge", WiredConditionUserWearsBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_time_less_than", WiredConditionLessTimeElapsed.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_match_snapshot", WiredConditionMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_time_more_than", WiredConditionMoreTimeElapsed.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_furni_on", WiredConditionNotFurniHaveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_hv_avtrs", WiredConditionNotFurniHaveUser.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_stuff_is", WiredConditionNotFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_user_count", WiredConditionNotUserCount.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_wearing_fx", WiredConditionNotUserHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_wearing_b", WiredConditionNotUserWearsBadge.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_in_group", WiredConditionNotInGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_in_team", WiredConditionNotInTeam.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_match_snap", WiredConditionNotMatchStatePosition.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_trggrer_on", WiredConditionNotTriggerOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_actor_in_team", WiredConditionTeamMember.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_trggrer_on_frn", WiredConditionTriggerOnFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_has_handitem", WiredConditionUserHasHandItem.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_has_handitem", WiredConditionNotUserHasHandItem.class));
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
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_level", WiredConditionUserLevel.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_has_rank", WiredConditionUserHasRank.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_habbo_has_rank", WiredConditionNotUserHasRank.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_user_cooldown", WiredConditionUserCooldown.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_first_trg", WiredConditionUserFirstTime.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_daily_trg", WiredConditionUserDaily.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_furni_opacity_is", WiredConditionFurniOpacityIs.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_furni_opacity_is", WiredConditionNotFurniOpacityIs.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_x_points_leaderboard", WiredConditionUserHasHighscorePoints.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_x_points_leaderboard", WiredConditionNotUserHasHighscorePoints.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_neg_has_var", WiredConditionNotHasVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_var_val_match", WiredConditionVariableValueMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_var_age_match", WiredConditionVariableAgeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_check_array", WiredConditionCheckArray.class));
        // Player-facing wired chest (Scrigno) — currency + furni storage
        this.interactionsList.add(new ItemInteraction("wf_storage_coins1", InteractionWiredChestCurrency.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_coins2", InteractionWiredChestCurrency.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_furni1", InteractionWiredChestFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_furni2", InteractionWiredChestFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_storage_furni_starter", InteractionWiredChestFurni.class));

        // Wired chest trading/contracts: give-from-chest, has-item conditions, scanner,
        // transactions, place/remove-furni, contracts and quests. Each key is registered once;
        // historical contract persistence is read by the canonical classes.
        this.interactionsList.add(new ItemInteraction("wf_act_give_currency", WiredEffectGiveCurrencyFromChest.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_chest_has_items", WiredConditionChestHasItems.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_furni", WiredEffectGiveFurniFromChest.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_chest_has_item_type", WiredConditionChestHasItemType.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_scan_chest_furni_by_type", WiredExtraChestItemTypeScanner.class));
        this.interactionsList.add(new ItemInteraction("wf_act_init_transaction", WiredEffectInitTransaction.class));
        this.interactionsList.add(new ItemInteraction("wf_act_cancel_transaction", WiredEffectCancelTransaction.class));
        this.interactionsList.add(
                new ItemInteraction("wf_trg_transaction_complete", WiredTriggerTransactionComplete.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_transaction_fail", WiredTriggerTransactionFail.class));
        this.interactionsList.add(new ItemInteraction("wf_act_place_furni", WiredEffectPlaceTemporaryFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_spawn_furni", WiredEffectPlaceFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_remove_furni", WiredEffectRemoveTemporaryFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_pickup_furni", WiredEffectRemoveFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_payment", InteractionWiredContractPayment.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_reward", InteractionWiredContractReward.class));
        this.interactionsList.add(new ItemInteraction("wf_contract_trade", InteractionWiredContractTrade.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_custom_contract", InteractionWiredCustomContract.class));

        // Fifteen finished wired classes had no line here, so no furni could ever reach them and the
        // furni already carrying these interaction types resolved to nothing. Each has a client dialog
        // and a type code; only the binding was missing.
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_battlebanzai", WiredConditionNoBattleBanzaiRunning.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_battlebz", WiredConditionNoBattleBanzaiRunning.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_trg_frn_adjacent_state", WiredConditionTriggerFurniAdjacentState.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_user_on_furni_with_state", WiredConditionUserOnFurniWithState.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_start_dance", WiredEffectBotDance.class));
        this.interactionsList.add(new ItemInteraction("wf_act_bot_stop_dance", WiredEffectBotDance.class));
        this.interactionsList.add(new ItemInteraction(
                "wf_act_give_hotelview_bonus_rare_points", WiredEffectGiveHotelviewBonusRarePoints.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_give_hotelview_hof_points", WiredEffectGiveHotelviewHofPoints.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_or_take_furni", WiredEffectGiveOrTakeFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_give_points_type", WiredEffectGivePointsType.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_give_points_highscore", WiredEffectGivePointsHighscore.class));
        this.interactionsList.add(new ItemInteraction("wf_act_play_youtube_sound", WiredEffectPlayYoutube.class));
        this.interactionsList.add(new ItemInteraction("wf_act_quick_bopper", WiredEffectQuickBopper.class));
        this.interactionsList.add(new ItemInteraction("wf_act_roller_speed", WiredEffectSetRollerSpeed.class));
        this.interactionsList.add(new ItemInteraction("wf_act_set_room_ad", WiredEffectSetRoomAd.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_dice_rolled", WiredTriggerDiceRolled.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_press_keybind", WiredTriggerPressKeybind.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_gets_handitem", WiredTriggerUserGetsHandItem.class));
        // The positive half of three conditions that only existed as negatives, so a furni named for
        // the positive reading had to point at a class that answered the opposite.
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_has_credits", WiredConditionUserHasCredits.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_has_duckets", WiredConditionUserHasDuckets.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_battlebanzai", WiredConditionBattleBanzaiRunning.class));
        this.interactionsList.add(new ItemInteraction("wf_act_override_height", WiredEffectOverrideHeight.class));
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
        this.interactionsList.add(new ItemInteraction("wf_xtra_varfx_hp", WiredExtraVariableFxHealthPoints.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_fx_health", WiredExtraVariableFxHealthPoints.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_varfx_prog", WiredExtraVariableFxProgressBar.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_var_fx_progress", WiredExtraVariableFxProgressBar.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_varfx_levelling", WiredExtraVariableFxLevellingProgress.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_var_fx_level", WiredExtraVariableFxLevellingProgress.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_varfx_status", WiredExtraVariableFxStatusBar.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_fx_status", WiredExtraVariableFxStatusBar.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_varfx_boss", WiredExtraVariableFxBossBar.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_fx_boss", WiredExtraVariableFxBossBar.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_varfx_number", WiredExtraVariableFxNumberDisplay.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_var_fx_number", WiredExtraVariableFxNumberDisplay.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_var_lvlup_system", WiredExtraVariableLevelUpSystem.class));
        this.interactionsList.add(new ItemInteraction("wf_var_user", WiredExtraUserVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_furni", WiredExtraFurniVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_room", WiredExtraRoomVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_context", WiredExtraContextVariable.class));
        this.interactionsList.add(new ItemInteraction("wf_var_reference", WiredExtraVariableReference.class));
        this.interactionsList.add(new ItemInteraction("wf_var_echo", WiredExtraVariableEcho.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_array_capture_variable", WiredExtraArrayCaptureVariable.class));

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
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_is_male", WiredConditionUserIsMale.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_is_female", WiredConditionUserIsFemale.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_habbo_has_rights", WiredConditionUserHasRights.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_habbo_has_rights", WiredConditionUserNotHasRights.class));
        this.interactionsList.add(new ItemInteraction("wf_act_set_state", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_set_trg_state", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_open_gates", WiredEffectMatchFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_close_dice", WiredEffectToggleFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_act_roll_dice", WiredEffectRollDice.class));
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
        this.interactionsList.add(new ItemInteraction("wf_trg_click_bot", WiredTriggerUserClicksUser.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_double_click_furni", WiredTriggerUserClicksFurni.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_anti_afk", WiredTriggerUserUnidles.class));
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
                new ItemInteraction("wf_act_bot_talk_to_avatar_custom", WiredEffectBotTalkToUser.class));
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
        this.interactionsList.add(new ItemInteraction("wf_cnd_tgr_furni_hv_avtrs", WiredConditionFurniHaveUser.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_tgr_furni_hv_avtrs", WiredConditionNotFurniHaveUser.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wears_effect", WiredConditionUserHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_not_wears_effect", WiredConditionNotUserHasEffect.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_wears_handitem", WiredConditionUserHasHandItem.class));
        this.interactionsList.add(
                new ItemInteraction("wf_cnd_not_wears_handitem", WiredConditionNotUserHasHandItem.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_trgr_stuff_matches", WiredConditionFurniTypeMatch.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_cnd_collision", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_exits_room", WiredTriggerUserLeavesRoom.class));
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
                new ItemInteraction("wf_cnd_furnis_hv_avtrs_custom", WiredConditionFurniHaveUser.class));
        this.interactionsList.add(new ItemInteraction("wf_cnd_x_habbos_on_furni", WiredConditionFurniHaveUser.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_exact_keyword", WiredTriggerUserSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_says_command", WiredTriggerUserSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_habbo_says_command", WiredTriggerUserSaysKeyword.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_other_collides_user", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_collides_bot", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_user_collides_other", WiredTriggerCollision.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_one_condition", WiredExtraOrEval.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_furni_as_group", WiredEffectMoveAsGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_act_move_group_step", WiredEffectMoveFurniAsGroup.class));
        this.interactionsList.add(new ItemInteraction("wf_slc_remote", WiredEffectRemoteSelector.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_mov_curve", WiredExtraMovementCurve.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_time_util", WiredExtraTimeUtilities.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_web_api", WiredExtraVariableWebApi.class));
        // ---- end inert-furni group ----

        this.interactionsList.add(new ItemInteraction("wf_highscore", InteractionWiredHighscore.class));
        this.interactionsList.add(new ItemInteraction("wf_act_change_opacity", WiredEffectChangeOpacity.class));
        // Last among the wired ones so no registered wired interaction changes position.
        // Habbo sells the projectile add-on under this name; it turns what the stack moves to face its flight.
        this.interactionsList.add(new ItemInteraction("wf_xtra_rotate_to_dir", WiredExtraProjectile.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_progress_achievement", WiredEffectProgressAchievement.class));
        this.interactionsList.add(
                new ItemInteraction("wf_xtra_achievement_enabler", WiredExtraAchievementEnabler.class));
        this.interactionsList.add(
                new ItemInteraction("wf_act_progress_reward_track", WiredEffectProgressRewardTrack.class));
        this.interactionsList.add(new ItemInteraction("wf_act_reset_reward_track", WiredEffectResetRewardTrack.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_text_output_global", WiredExtraTextOutputGlobal.class));
        this.interactionsList.add(new ItemInteraction("wf_var_daily_task", WiredExtraDailyTask.class));

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
        this.interactionsList.add(new ItemInteraction("wf_room_linker", InteractionWiredRoomLinker.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_leaderboard", WiredExtraVariableLeaderboard.class));
        this.interactionsList.add(new ItemInteraction("wf_label", WiredExtraLabel.class));
        this.interactionsList.add(new ItemInteraction("recolorable", InteractionRecolorable.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_room_initialized", WiredTriggerRoomInitialized.class));
        // Belongs beside wf_act_change_opacity, but sits last so no registered wired interaction moves.
        this.interactionsList.add(new ItemInteraction("wf_act_set_highlight", WiredEffectSetHighlight.class));
        // Belongs beside wf_var_echo, last for the same reason.
        this.interactionsList.add(new ItemInteraction("wf_var_pointer", WiredExtraPointerVariable.class));
        // The WIRED Link furni and its boxes, last for the same reason.
        this.interactionsList.add(new ItemInteraction("wf_link", InteractionWiredLink.class));
        this.interactionsList.add(new ItemInteraction("wf_act_send_message", WiredEffectSendLinkMessage.class));
        this.interactionsList.add(new ItemInteraction("wf_act_send_dataframe", WiredEffectSendLinkDataframe.class));
        this.interactionsList.add(new ItemInteraction("wf_trg_receive_message", WiredTriggerReceiveLinkMessage.class));
        this.interactionsList.add(
                new ItemInteraction("wf_trg_receive_dataframe", WiredTriggerReceiveLinkDataframe.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_link_speaker", WiredExtraLinkSpeaker.class));
        // The variable add-on share and import boxes, last for the same reason.
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_share", WiredExtraVariableAddonShare.class));
        this.interactionsList.add(new ItemInteraction("wf_xtra_var_import", WiredExtraVariableAddonImport.class));
    }

    public void addItemInteraction(ItemInteraction itemInteraction) {
        this.interactionsList.addChecked(itemInteraction);
    }

    public ItemInteraction getItemInteraction(Class<? extends UserItem> type) {
        ItemInteraction interaction = this.interactionsList.find(type);
        if (interaction != null) {
            return interaction;
        }

        LOGGER.debug("Can't find interaction class: {}", type.getName());
        return this.getItemInteraction(InteractionDefault.class);
    }

    /**
     * Our wired boxes that share an official box's interaction type: the furni's name picks ours. So an
     * items_base row {@code item_name = wf_act_pickup_furni, interaction_type = wf_act_remove_furni} is our
     * pickup box, while every other row with that interaction type is the official one. Their own
     * interaction types keep working as well.
     */
    static final Map<String, String> NAMED_VARIANTS = Map.of(
            "wf_act_pickup_furni", "wf_act_remove_furni",
            "wf_act_spawn_furni", "wf_act_place_furni",
            "wf_act_move_group_step", "wf_act_move_furni_as_group");

    /** The interaction a furni named {@code itemName} gets on {@code interactionType}. */
    public ItemInteraction resolveInteraction(String interactionType, String itemName) {
        String type = interactionType == null ? "default" : interactionType.toLowerCase();
        String name = itemName == null ? "" : itemName.toLowerCase();
        if (type.equals(NAMED_VARIANTS.get(name))) {
            ItemInteraction variant = this.interactionsList.find(name);
            if (variant != null) return variant;
        }
        return this.getItemInteraction(type);
    }

    public ItemInteraction getItemInteraction(String type) {
        ItemInteraction interaction = this.interactionsList.find(type);
        if (interaction != null) {
            return interaction;
        }

        return this.getItemInteraction(InteractionDefault.class);
    }

    /**
     * Adds and updates the base items, and drops the ones whose row is gone. A base item deleted from the furni
     * editor used to stay in memory until the next restart.
     */
    public void loadItems() {
        IntSet loaded = new IntOpenHashSet();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(BASE_ITEMS_SQL)) {
            while (set.next()) {
                int id = set.getInt("id");
                loaded.add(id);
                try {
                    if (!this.items.containsKey(id)) this.items.put(id, new Item(set));
                    else this.items.get(id).update(set);
                } catch (Exception e) {
                    LOGGER.error("Failed to load Item ({})", id);
                    LOGGER.error("Caught exception", e);
                }
            }
        } catch (SQLException e) {
            // A partial read says nothing about which rows are gone: keep every item.
            LOGGER.error("Caught SQL exception", e);
            return;
        }

        synchronized (this.items) {
            this.items.keySet().removeIf((int id) -> !loaded.contains(id));
        }
    }

    public void loadCrackable() {
        Int2ObjectMap<CrackableReward> rewards = new Int2ObjectOpenHashMap<>();
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
                rewards.put(set.getInt("item_id"), reward);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            return;
        }
        this.crackableRewards = rewards;
    }

    public void loadPlants() {
        this.plantConfigs.clear();
        this.plantData.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM item_plants");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    PlantConfig config = new PlantConfig(set);
                    if (config.getItemName() != null) {
                        this.plantConfigs.put(config.getItemName().toLowerCase(), config);
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM item_plants_data");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.plantData.put(set.getInt("item_id"), new PlantData(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public PlantConfig getPlantConfig(String itemName) {
        return itemName == null ? null : this.plantConfigs.get(itemName.toLowerCase());
    }

    public PlantData getPlantData(int itemId) {
        return this.plantData.get(itemId);
    }

    public void putPlantData(int itemId, PlantData data) {
        this.plantData.put(itemId, data);
    }

    public void removePlantData(int itemId) {
        this.plantData.remove(itemId);
    }

    public int getCrackableCount(int itemId) {
        CrackableReward reward = this.crackableRewards.get(itemId);
        return reward == null ? 0 : reward.count;
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
        Map<String, SoundTrack> tracks = new ConcurrentHashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM soundtracks");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                tracks.put(set.getString("code"), new SoundTrack(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return;
        }
        this.soundTracks = tracks;
    }

    public SoundTrack getSoundTrack(String code) {
        return this.soundTracks.get(code);
    }

    public SoundTrack getSoundTrack(int id) {
        for (SoundTrack track : this.soundTracks.values()) {
            if (track.getId() == id) return track;
        }

        return null;
    }

    public void addSoundTrack(SoundTrack track) {
        this.soundTracks.put(track.getCode(), track);
    }

    public void removeSoundTrack(String code) {
        this.soundTracks.remove(code);
    }

    public Item getFirstItemByInteraction(Class<? extends UserItem> interactionClass) {
        for (Item item : this.items.values()) {
            if (item.getInteractionType() != null && item.getInteractionType().getType() == interactionClass) {
                return item;
            }
        }

        return null;
    }

    /** The owner of a furni still wrapped in a gift: no inventory loads it, and opening the gift gives it one. */
    public static final int GIFT_CONTENT_OWNER = 0;

    public UserItem createItem(int userId, Item item, int limitedStack, int limitedSells, String extraData) {
        if (userId <= 0 || item == null) {
            return null;
        }

        return this.insertItem(userId, userId, item, limitedStack, limitedSells, extraData);
    }

    /**
     * Creates a furni to be wrapped in a gift. It has no owner until the gift is opened, so it is in no
     * inventory and cannot be used, traded or sold while the gift still holds it. A recipient above zero is
     * held to the web-api box limit like any other new furni; zero is a staff gift.
     */
    public UserItem createGiftContent(
            int recipientId, Item item, int limitedStack, int limitedSells, String extraData) {
        if (item == null) {
            return null;
        }

        return this.insertItem(GIFT_CONTENT_OWNER, recipientId, item, limitedStack, limitedSells, extraData);
    }

    private UserItem insertItem(
            int ownerId, int limitOwnerId, Item item, int limitedStack, int limitedSells, String extraData) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            return this.insertItem(connection, ownerId, limitOwnerId, item, limitedStack, limitedSells, extraData);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return null;
    }

    public UserItem createItem(
            Connection connection, int userId, Item item, int limitedStack, int limitedSells, String extraData)
            throws SQLException {
        if (userId <= 0 || item == null) {
            return null;
        }

        return this.insertItem(connection, userId, userId, item, limitedStack, limitedSells, extraData);
    }

    private UserItem insertItem(
            Connection connection,
            int userId,
            int limitOwnerId,
            Item item,
            int limitedStack,
            int limitedSells,
            String extraData)
            throws SQLException {
        // Every reward, gift and wired path ends here, so a second web-api box cannot be made anywhere.
        if (limitOwnerId > 0
                && WiredWebApiOwnership.isWebApiItem(item)
                && !WiredWebApiOwnership.canOwnMore(limitOwnerId, 1)) {
            return null;
        }

        extraData = ItemDataGuard.normalizeExtraData(extraData);
        // A recolourable furni only ever starts in a colour of its palette, whatever the client asked for.
        if (InteractionRecolorable.isRecolorable(item)) {
            extraData = InteractionRecolorable.placementExtradata(item, extraData, "0");
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items (user_id, item_id, extra_data, limited_data) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, userId);
            statement.setInt(2, item.getId());
            statement.setString(3, extraData);
            statement.setString(4, limitedStack + ":" + limitedSells);
            statement.execute();

            try (ResultSet set = statement.getGeneratedKeys()) {
                if (set.next()) {
                    Class<? extends UserItem> itemClass =
                            item.getInteractionType().getType();

                    if (itemClass != null) {
                        try {
                            return itemClass
                                    .getDeclaredConstructor(
                                            int.class, int.class, Item.class, String.class, int.class, int.class)
                                    .newInstance(set.getInt(1), userId, item, extraData, limitedStack, limitedSells);
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                            return new InteractionDefault(
                                    set.getInt(1), userId, item, extraData, limitedStack, limitedSells);
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

    /**
     * Ids of temporary furni: the band the official client treats as temporary (owner "Temp (Wired)", never the
     * viewer's own furni). Far above any database id, and never written to the database. Behaviour observed in
     * plus-emulator/PlusEMU 55f02174, reimplemented.
     */
    public static final int FIRST_TEMPORARY_ITEM_ID = 2_147_401_728;

    public static final int LAST_TEMPORARY_ITEM_ID = 2_147_418_111;

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_TEMPORARY_ITEM_ID =
            new java.util.concurrent.atomic.AtomicInteger(FIRST_TEMPORARY_ITEM_ID);

    /** Whether {@code id} is in the temporary furni band. */
    public static boolean isTemporaryItemId(int id) {
        return id >= FIRST_TEMPORARY_ITEM_ID && id <= LAST_TEMPORARY_ITEM_ID;
    }

    /**
     * A temporary furni of {@code item}'s type (the official "place furni" wired): it lives in memory
     * only, has no owner, and is never saved, picked up into an inventory or deleted from the database.
     * The id comes from a hotel-wide counter in the band; Place Furni takes its ids from the room instead.
     */
    public UserItem createTemporaryItem(Item item, String extraData) {
        int id = NEXT_TEMPORARY_ITEM_ID.getAndUpdate(
                next -> next >= LAST_TEMPORARY_ITEM_ID ? FIRST_TEMPORARY_ITEM_ID : next + 1);
        return this.createTemporaryItem(id, item, extraData);
    }

    /** A temporary furni of {@code item}'s type with the given id from the temporary band. */
    public UserItem createTemporaryItem(int id, Item item, String extraData) {
        if (item == null || !isTemporaryItemId(id)) return null;

        String data = ItemDataGuard.normalizeExtraData(extraData);
        UserItem created;
        Class<? extends UserItem> itemClass = item.getInteractionType().getType();
        try {
            created = itemClass
                    .getDeclaredConstructor(int.class, int.class, Item.class, String.class, int.class, int.class)
                    .newInstance(id, 0, item, data, 0, 0);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            created = new InteractionDefault(id, 0, item, data, 0, 0);
        }
        created.markTemporary();
        return created;
    }

    public void deleteItem(UserItem item) {
        this.tryDeleteItem(item);
    }

    /**
     * Deletes the item's rows and says whether that worked, for an undo that must not hand back what it paid for when
     * the item is still there.
     *
     * @return false when the database refused; true when the rows are gone or there were none
     */
    public boolean tryDeleteItem(UserItem item) {
        if (item == null || item.isTemporary()) {
            // A temporary furni has no rows to delete.
            return true;
        }

        // A wired chest gives what it holds back to its owner before its row goes.
        ChestRelease.releaseToOwner(item);

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
            return false;
        }
        return true;
    }

    /**
     * Pays out a broken crackable: deletes its row (unless it resets in place) and creates the reward
     * in one transaction, so a failure leaves the crackable as it was and no reward behind.
     *
     * @param reward the reward furni, or null when the crackable gives nothing
     * @return the reward created, or null when there is none (no reward, or the owner's web-api limit)
     * @throws SQLException when nothing was written
     */
    public UserItem crackInTransaction(UserItem crackable, boolean deleteCrackable, int ownerId, Item reward)
            throws SQLException {
        return inTransaction(connection -> {
            if (deleteCrackable) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
                    statement.setInt(1, crackable.getId());
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Crackable " + crackable.getId() + " is already gone");
                    }
                }
            }

            return reward == null ? null : this.createItem(connection, ownerId, reward, 0, 0, "");
        });
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    /** Runs {@code work} on one connection and commits it, or rolls everything back when it throws. */
    private static <T> T inTransaction(TransactionWork<T> work) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public UserItem handleRecycle(User user, String itemId) {
        int rewardItemId = ItemDataGuard.parsePositiveInt(itemId);
        if (user == null
                || user.getUserInfo() == null
                || rewardItemId <= 0
                || Emulator.getGameEnvironment().getCatalogManager().ecotronItem == null) {
            return null;
        }

        return this.recycleInTransaction(user, rewardItemId, List.of());
    }

    /**
     * Recycles {@code consumed} into a recycler box holding {@code itemId}, in one transaction: the
     * consumed rows are deleted and the box with its prize row inserted together, or nothing is.
     *
     * <p>A crash or a database error therefore never leaves both the prize and the furni that paid
     * for it, nor a box whose prize row is missing.
     *
     * @return the box, or null when nothing was written
     */
    public UserItem handleRecycle(User user, String itemId, Collection<UserItem> consumed) {
        int rewardItemId = ItemDataGuard.parsePositiveInt(itemId);
        if (user == null
                || user.getUserInfo() == null
                || rewardItemId <= 0
                || consumed == null
                || consumed.isEmpty()
                || Emulator.getGameEnvironment().getCatalogManager().ecotronItem == null) {
            return null;
        }

        // A chest gives back what it holds before its row goes, as QueryDeleteUserItem does.
        for (UserItem item : consumed) {
            ChestRelease.releaseToOwner(item);
        }

        return this.recycleInTransaction(user, rewardItemId, consumed);
    }

    private UserItem recycleInTransaction(User user, int rewardItemId, Collection<UserItem> consumed) {
        int userId = user.getUserInfo().getId();
        try {
            return inTransaction(connection -> {
                this.deleteOwnedItems(connection, userId, consumed);
                return this.insertRecycleBox(connection, userId, rewardItemId);
            });
        } catch (SQLException e) {
            LOGGER.error(
                    "Recycling for user {} failed, nothing was written",
                    user.getUserInfo().getId(),
                    e);
            return null;
        }
    }

    /**
     * Crafts {@code reward} out of {@code consumed} in one transaction: the ingredients' rows are
     * deleted and the reward's row inserted together, or nothing is.
     *
     * @return the reward, or null when nothing was written
     */
    public UserItem craftInTransaction(int userId, Item reward, Collection<UserItem> consumed) {
        if (userId <= 0 || reward == null || consumed == null || consumed.isEmpty()) {
            return null;
        }

        // A chest gives back what it holds before its row goes, as QueryDeleteUserItem does.
        for (UserItem item : consumed) {
            ChestRelease.releaseToOwner(item);
        }

        try {
            return inTransaction(connection -> {
                this.deleteOwnedItems(connection, userId, consumed);
                UserItem created = this.createItem(connection, userId, reward, 0, 0, "");
                if (created == null) {
                    throw new SQLException("Crafting reward " + reward.getId() + " was refused for user " + userId);
                }
                return created;
            });
        } catch (SQLException e) {
            LOGGER.error("Crafting for user {} failed, nothing was written", userId, e);
            return null;
        }
    }

    private void deleteOwnedItems(Connection connection, int userId, Collection<UserItem> consumed)
            throws SQLException {
        if (consumed.isEmpty()) {
            return;
        }

        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM items WHERE id = ? AND user_id = ?")) {
            for (UserItem item : consumed) {
                statement.setInt(1, item.getId());
                statement.setInt(2, userId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Item " + item.getId() + " is not owned by user " + userId);
                }
            }
        }
    }

    private UserItem insertRecycleBox(Connection connection, int userId, int rewardItemId) throws SQLException {
        String extradata = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "-"
                + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "-"
                + Calendar.getInstance().get(Calendar.YEAR);
        Item ecotron = Emulator.getGameEnvironment().getCatalogManager().ecotronItem;

        int boxId;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items (user_id, item_id, extra_data) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, userId);
            statement.setInt(2, ecotron.getId());
            statement.setString(3, extradata);
            statement.execute();

            try (ResultSet set = statement.getGeneratedKeys()) {
                if (!set.next()) {
                    throw new SQLException("Recycler box was not created");
                }
                boxId = set.getInt(1);
            }
        }

        try (PreparedStatement statement =
                connection.prepareStatement("INSERT INTO items_presents (item_id, base_item_reward) VALUES (?, ?)")) {
            statement.setInt(1, boxId);
            statement.setInt(2, rewardItemId);
            statement.executeUpdate();
        }

        return new InteractionDefault(boxId, userId, ecotron, extradata, 0, 0);
    }

    /** The reward of opening {@code box}, or null when nothing came out; see {@link #openRecycleBox}. */
    public UserItem handleOpenRecycleBox(User user, UserItem box) {
        return this.openRecycleBox(user, box).reward();
    }

    /** Opens a recycler box in one transaction; a failure leaves the box where it was. */
    public RecycleBoxOpening openRecycleBox(User user, UserItem box) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            return RecycleBoxOpening.open(connection, this, user.getUserInfo().getId(), box);
        } catch (SQLException | RuntimeException e) {
            LOGGER.error("Opening recycler box {} failed", box.getId(), e);
            return RecycleBoxOpening.FAILED;
        }
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

    public void insertHopper(UserItem hopper) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            this.insertHopper(connection, hopper);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void insertHopper(Connection connection, UserItem hopper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO items_hoppers VALUES (?, ?)")) {
            statement.setInt(1, hopper.getId());
            statement.setInt(2, hopper.getBaseItem().getId());
            statement.execute();
        }
    }

    public int[] getTargetTeleportRoomId(UserItem item) {
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

    public UserItem loadUserItem(int itemId) {
        UserItem item = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    item = this.loadUserItem(set);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return item;
    }

    public UserItem loadUserItem(ResultSet set) throws SQLException {
        Item baseItem = this.getItem(set.getInt("item_id"));

        if (baseItem == null) return null;

        Class<? extends UserItem> itemClass = baseItem.getInteractionType().getType();

        if (itemClass != null) {
            try {
                Constructor<?> c = itemClass.getConstructor(ResultSet.class, Item.class);
                c.setAccessible(true);

                return (UserItem) c.newInstance(set, baseItem);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        return null;
    }

    public UserItem createGift(String username, Item item, String extraData, int limitedStack, int limitedSells) {
        if (username == null || username.isBlank() || item == null) {
            return null;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(username);

        int userId = 0;

        if (user != null) {
            userId = user.getUserInfo().getId();
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

    public UserItem createGift(int userId, Item item, String extraData, int limitedStack, int limitedSells) {
        if (userId <= 0 || item == null) return null;

        if (extraData != null && extraData.length() > ItemDataGuard.MAX_EXTRA_DATA_LENGTH) {
            LOGGER.error("Extradata exceeds maximum length of 1000 characters: {}", extraData);
        }
        extraData = ItemDataGuard.normalizeExtraData(extraData);

        UserItem gift = this.createItem(userId, item, limitedStack, limitedSells, extraData);

        if (gift != null) {
            User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);
            if (user != null) {
                user.getInventory().getItemsComponent().addItem(gift);
                user.getClient().sendResponse(new AddUserItemComposer(gift));
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
