package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CommandLog;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetCommand;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTypingComposer;
import com.eu.habbo.plugin.events.users.UserCommandEvent;
import com.eu.habbo.plugin.events.users.UserExecuteCommandEvent;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public class CommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandler.class);

    private final static THashMap<String, Command> commands = new THashMap<>(5);
    private static final Comparator<Command> ALPHABETICAL_ORDER = new Comparator<Command>() {
        public int compare(Command c1, Command c2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(c1.permission, c2.permission);
            return (res != 0) ? res : c1.permission.compareTo(c2.permission);
        }
    };

    public CommandHandler() {
        long millis = System.currentTimeMillis();
        this.reloadCommands();
        LOGGER.info("Command Handler -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public static void addCommand(Command command) {
        if (command == null)
            return;

        commands.put(command.getClass().getName(), command);
    }


    public static void addCommand(Class<? extends Command> command) {
        try {
            //command.getConstructor().setAccessible(true);
            addCommand(command.getDeclaredConstructor().newInstance());
            LOGGER.debug("Added command: {}", command.getName());
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }


    public static boolean handleCommand(GameClient gameClient, String commandLine) {
        if (gameClient != null && commandLine != null) {
            if (commandLine.startsWith(":")) {
                commandLine = commandLine.replaceFirst(":", "");

                String[] parts = commandLine.split(" ");

                if (parts.length >= 1) {
                    for (Command command : commands.values()) {
                        for (String s : command.keys) {
                            if (s.equalsIgnoreCase(parts[0])) {
                                boolean succes = false;
                                if (command.permission == null || gameClient.getHabbo().hasPermission(command.permission, gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null && (gameClient.getHabbo().getHabboInfo().getCurrentRoom().hasRights(gameClient.getHabbo())) || gameClient.getHabbo().hasPermission(Permission.ACC_PLACEFURNI) || (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null && gameClient.getHabbo().getHabboInfo().getCurrentRoom().getGuildId() > 0 && gameClient.getHabbo().getHabboInfo().getCurrentRoom().getGuildRightLevel(gameClient.getHabbo()).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS)))) {
                                    try {
                                        UserExecuteCommandEvent userExecuteCommandEvent = new UserExecuteCommandEvent(gameClient.getHabbo(), command, parts);
                                        Emulator.getPluginManager().fireEvent(userExecuteCommandEvent);

                                        if(userExecuteCommandEvent.isCancelled()) {
                                            return userExecuteCommandEvent.isSuccess();
                                        }

                                        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null)
                                            gameClient.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserTypingComposer(gameClient.getHabbo().getRoomUnit(), false).compose());

                                        UserCommandEvent event = new UserCommandEvent(gameClient.getHabbo(), parts, command.handle(gameClient, parts));
                                        Emulator.getPluginManager().fireEvent(event);

                                        succes = event.succes;
                                    } catch (Exception e) {
                                        LOGGER.error("Caught exception", e);
                                    }

                                    if (gameClient.getHabbo().getHabboInfo().getRank().isLogCommands()) {
                                        Emulator.getDatabaseLogger().store(new CommandLog(gameClient.getHabbo().getHabboInfo().getId(), command, commandLine, succes));
                                    }
                                }

                                return succes;
                            }
                        }
                    }
                }
            } else {
                String[] args = commandLine.split(" ");

                if (args.length <= 1)
                    return false;

                if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                    Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

                    if (room.getCurrentPets().isEmpty())
                        return false;

                    TIntObjectIterator<Pet> petIterator = room.getCurrentPets().iterator();

                    for (int j = room.getCurrentPets().size(); j-- > 0; ) {
                        try {
                            petIterator.advance();
                        } catch (NoSuchElementException e) {
                            break;
                        }

                        Pet pet = petIterator.value();

                        if (pet != null) {
                            if (pet.getName().equalsIgnoreCase(args[0])) {
                                StringBuilder s = new StringBuilder();

                                for (int i = 1; i < args.length; i++) {
                                    s.append(args[i]).append(" ");
                                }

                                s = new StringBuilder(s.substring(0, s.length() - 1));

                                for (PetCommand command : pet.getPetData().getPetCommands()) {
                                    if (command.key.equalsIgnoreCase(s.toString())) {
                                        if (pet instanceof RideablePet && ((RideablePet) pet).getRider() != null) {
                                            if (((RideablePet) pet).getRider().getHabboInfo().getId() == gameClient.getHabbo().getHabboInfo().getId()) {
                                                ((RideablePet) pet).getRider().getHabboInfo().dismountPet();
                                            }
                                            break;
                                        }

                                        if (command.level <= pet.getLevel())
                                            pet.handleCommand(command, gameClient.getHabbo(), args);
                                        else
                                            pet.say(pet.getPetData().randomVocal(PetVocalsType.UNKNOWN_COMMAND));

                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static Command getCommand(String key) {
        for (Command command : commands.values()) {
            for (String k : command.keys) {
                if (key.equalsIgnoreCase(k)) {
                    return command;
                }
            }
        }

        return null;
    }

    public void reloadCommands() {
        addCommand(new AboutCommand());
        addCommand(new AlertCommand());
        addCommand(new AllowTradingCommand());
        addCommand(new ArcturusCommand());
        addCommand(new BadgeCommand());
        addCommand(new BanCommand());
        addCommand(new BlockAlertCommand());
        addCommand(new BotsCommand());
        addCommand(new CalendarCommand());
        addCommand(new ChatTypeCommand());
        addCommand(new CommandsCommand());
        addCommand(new ControlCommand());
        addCommand(new CoordsCommand());
        addCommand(new CreditsCommand());
        addCommand(new DanceCommand());
        addCommand(new DiagonalCommand());
        addCommand(new DisconnectCommand());
        addCommand(new EjectAllCommand());
        addCommand(new EmptyInventoryCommand());
        addCommand(new EmptyBotsInventoryCommand());
        addCommand(new EmptyPetsInventoryCommand());
        addCommand(new EmuStatsCommand());
        addCommand(new EnableCommand());
        addCommand(new EventCommand());
        addCommand(new FacelessCommand());
        addCommand(new FastwalkCommand());
        addCommand(new FilterWordCommand());
        addCommand(new FreezeBotsCommand());
        addCommand(new FreezeCommand());
        addCommand(new GiftCommand());
        addCommand(new GiveRankCommand());
        addCommand(new HabnamCommand());
        addCommand(new HandItemCommand());
        addCommand(new HappyHourCommand());
        addCommand(new HideWiredCommand());
        addCommand(new HotelAlertCommand());
        addCommand(new HotelAlertLinkCommand());
        addCommand(new InvisibleCommand());
        addCommand(new IPBanCommand());
        addCommand(new LayCommand());
        addCommand(new MachineBanCommand());
        addCommand(new MassBadgeCommand());
        addCommand(new RoomBadgeCommand());
        addCommand(new MassCreditsCommand());
        addCommand(new MassGiftCommand());
        addCommand(new MassPixelsCommand());
        addCommand(new MassPointsCommand());
        addCommand(new MimicCommand());
        addCommand(new MoonwalkCommand());
        addCommand(new MultiCommand());
        addCommand(new MuteBotsCommand());
        addCommand(new MuteCommand());
        addCommand(new MutePetsCommand());
        addCommand(new PetInfoCommand());
        addCommand(new PickallCommand());
        addCommand(new PingCommand());
        addCommand(new PixelCommand());
        addCommand(new PluginsCommand());
        addCommand(new PointsCommand());
        addCommand(new PromoteTargetOfferCommand());
        addCommand(new PullCommand());
        addCommand(new PushCommand());
        addCommand(new RedeemCommand());
        addCommand(new ReloadRoomCommand());
        addCommand(new RoomAlertCommand());
        addCommand(new RoomBundleCommand());
        addCommand(new RoomCreditsCommand());
        addCommand(new RoomDanceCommand());
        addCommand(new RoomEffectCommand());
        addCommand(new RoomItemCommand());
        addCommand(new RoomKickCommand());
        addCommand(new RoomMuteCommand());
        addCommand(new RoomPixelsCommand());
        addCommand(new RoomPointsCommand());
        addCommand(new SayAllCommand());
        addCommand(new SayCommand());
        addCommand(new SetMaxCommand());
        addCommand(new SetPollCommand());
        addCommand(new SetRoomTemplateCommand());
        addCommand(new SetSpeedCommand());
        addCommand(new ShoutAllCommand());
        addCommand(new ShoutCommand());
        addCommand(new ShutdownCommand());
        addCommand(new SitCommand());
        addCommand(new StandCommand());
        addCommand(new SitDownCommand());
        addCommand(new StaffAlertCommand());
        addCommand(new StaffOnlineCommand());
        addCommand(new StalkCommand());
        addCommand(new SummonCommand());
        addCommand(new SummonRankCommand());
        addCommand(new SuperbanCommand());
        addCommand(new SuperPullCommand());
        addCommand(new TakeBadgeCommand());
        addCommand(new TeleportCommand());
        addCommand(new TransformCommand());
        addCommand(new TrashCommand());
        addCommand(new UnbanCommand());
        addCommand(new UnloadRoomCommand());
        addCommand(new UnmuteCommand());
        addCommand(new UpdateAllCommand());
        addCommand(new UpdateAchievements());
        addCommand(new UpdateBotsCommand());
        addCommand(new UpdateCalendarCommand());
        addCommand(new UpdateCatalogCommand());
        addCommand(new UpdateConfigCommand());
        addCommand(new UpdateGuildPartsCommand());
        addCommand(new UpdateHotelViewCommand());
        addCommand(new UpdateItemsCommand());
        addCommand(new UpdateNavigatorCommand());
        addCommand(new UpdatePermissionsCommand());
        addCommand(new UpdatePetDataCommand());
        addCommand(new UpdatePluginsCommand());
        addCommand(new UpdatePollsCommand());
        addCommand(new UpdateTextsCommand());
        addCommand(new UpdateWordFilterCommand());
        addCommand(new UserInfoCommand());
        addCommand(new WordQuizCommand());
        addCommand(new UpdateYoutubePlaylistsCommand());
        addCommand(new AddYoutubePlaylistCommand());
        addCommand(new SoftKickCommand());
        addCommand(new SubscriptionCommand());
        addCommand(new UpdateChatBubblesCommand());
        addCommand(new GivePrefixCommand());
        addCommand(new ListPrefixesCommand());
        addCommand(new RemovePrefixCommand());
        addCommand(new PrefixBlacklistCommand());
        addCommand(new WiredCommand());
        addCommand(new TestCommand());
    }

    public List<Command> getCommandsForRank(int rankId) {
        List<Command> allowedCommands = new ArrayList<>();
        if (Emulator.getGameEnvironment().getPermissionsManager().rankExists(rankId)) {
            THashMap<String, Permission> permissions = Emulator.getGameEnvironment().getPermissionsManager().getRank(rankId).getPermissions();

            for (Command command : commands.values()) {
                if (allowedCommands.contains(command))
                    continue;

                if (permissions.contains(command.permission) && permissions.get(command.permission).setting != PermissionSetting.DISALLOWED) {
                    allowedCommands.add(command);
                }
            }
        }

        allowedCommands.sort(CommandHandler.ALPHABETICAL_ORDER);

        return allowedCommands;
    }

    public void dispose() {
        commands.clear();
        LOGGER.info("Command Handler -> Disposed!");
    }
}
