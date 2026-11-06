package polaris.messages.outgoing.rooms.users;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.guilds.Guild;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.User;
import polaris.hotel.users.UserCustomizationData;
import polaris.hotel.users.inventory.BadgeRanks;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUsersComposer extends MessageComposer {
    private User user;
    private Collection<User> users;
    private Bot bot;
    private Collection<Bot> bots;

    public RoomUsersComposer(User user) {
        this.user = user;
    }

    public RoomUsersComposer(Collection<User> users) {
        this.users = users;
    }

    public RoomUsersComposer(Bot bot) {
        this.bot = bot;
    }

    public RoomUsersComposer(Collection<Bot> bots, boolean isBot) {
        this.bots = bots;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUsersComposer);
        if (this.user != null) {
            this.response.appendInt(1);
            this.response.appendInt(this.user.getUserInfo().getId());
            this.response.appendString(this.user.getUserInfo().getUsername());
            this.response.appendString(this.user.getUserInfo().getMotto());
            this.response.appendInt(this.user.getUserInfo().getInfostandBg());
            this.response.appendInt(this.user.getUserInfo().getInfostandStand());
            this.response.appendInt(this.user.getUserInfo().getInfostandOverlay());
            this.response.appendInt(this.user.getUserInfo().getInfostandCardBg());
            this.response.appendString(this.user.getUserInfo().getLook());
            this.response.appendInt(this.user.getRoomUnit().getId()); // Room Unit ID
            this.response.appendInt(this.user.getRoomUnit().getX());
            this.response.appendInt(this.user.getRoomUnit().getY());
            this.response.appendString(this.user.getRoomUnit().getZ() + "");
            this.response.appendInt(this.user.getRoomUnit().getBodyRotation().getValue());
            this.response.appendInt(1);
            this.response.appendString(
                    this.user.getUserInfo().getGender().name().toUpperCase());
            this.response.appendInt(this.user.getUserStats().guild != 0 ? this.user.getUserStats().guild : -1);
            this.response.appendInt(this.user.getUserStats().guild != 0 ? 1 : -1);

            String name = "";
            if (this.user.getUserStats().guild != 0) {
                Guild g = Emulator.getGameEnvironment().getGuildManager().getGuild(this.user.getUserStats().guild);

                if (g != null) name = g.getName();
            }
            this.response.appendString(name);

            this.response.appendString("");
            this.response.appendInt(this.user.getUserStats().getAchievementScore());
            this.response.appendBoolean(true);
            UserCustomizationData customizationData = UserCustomizationData.fromUser(this.user);
            this.response.appendString(customizationData.nickIcon);
            this.response.appendString(customizationData.prefixText);
            this.response.appendString(customizationData.prefixColor);
            this.response.appendString(customizationData.prefixIcon);
            this.response.appendString(customizationData.prefixEffect);
            this.response.appendString(customizationData.prefixFont);
            this.response.appendString(customizationData.displayOrder);
            // Official badgesRank: place on the total-badges leaderboard, -1 if unknown.
            this.response.appendInt(BadgeRanks.rankOf(this.user.getUserInfo().getId()));
            this.response.appendString(this.user.getUserInfo().getRoomEntryMethod());
            this.response.appendInt(
                    BuildersClubItemIds.toWire(this.user.getUserInfo().getRoomEntryTeleportId()));
            this.response.appendInt(this.user.getUserInfo().getInfostandBorder());
        } else if (this.users != null) {
            // The count is of what is written: a null entry was skipped but counted, and the client then read the
            // next packet's bytes as a user and dropped the whole roster.
            List<User> users = this.users.stream().filter(Objects::nonNull).toList();
            this.response.appendInt(users.size());
            for (User user : users) {
                if (user != null) {
                    this.response.appendInt(user.getUserInfo().getId());
                    this.response.appendString(user.getUserInfo().getUsername());
                    this.response.appendString(user.getUserInfo().getMotto());
                    this.response.appendInt(user.getUserInfo().getInfostandBg());
                    this.response.appendInt(user.getUserInfo().getInfostandStand());
                    this.response.appendInt(user.getUserInfo().getInfostandOverlay());
                    this.response.appendInt(user.getUserInfo().getInfostandCardBg());
                    this.response.appendString(user.getUserInfo().getLook());
                    this.response.appendInt(user.getRoomUnit().getId()); // Room Unit ID
                    this.response.appendInt(user.getRoomUnit().getX());
                    this.response.appendInt(user.getRoomUnit().getY());
                    this.response.appendString(user.getRoomUnit().getZ() + "");
                    this.response.appendInt(user.getRoomUnit().getBodyRotation().getValue());
                    this.response.appendInt(1);
                    this.response.appendString(
                            user.getUserInfo().getGender().name().toUpperCase());
                    this.response.appendInt(user.getUserStats().guild != 0 ? user.getUserStats().guild : -1);
                    this.response.appendInt(user.getUserStats().guild != 0 ? 1 : -1);
                    String name = "";
                    if (user.getUserStats().guild != 0) {
                        Guild g =
                                Emulator.getGameEnvironment().getGuildManager().getGuild(user.getUserStats().guild);

                        if (g != null) name = g.getName();
                    }
                    this.response.appendString(name);
                    this.response.appendString("");
                    this.response.appendInt(user.getUserStats().getAchievementScore());
                    this.response.appendBoolean(true);
                    UserCustomizationData customizationData = UserCustomizationData.fromUser(user);
                    this.response.appendString(customizationData.nickIcon);
                    this.response.appendString(customizationData.prefixText);
                    this.response.appendString(customizationData.prefixColor);
                    this.response.appendString(customizationData.prefixIcon);
                    this.response.appendString(customizationData.prefixEffect);
                    this.response.appendString(customizationData.prefixFont);
                    this.response.appendString(customizationData.displayOrder);
                    this.response.appendInt(BadgeRanks.rankOf(user.getUserInfo().getId()));
                    this.response.appendString(user.getUserInfo().getRoomEntryMethod());
                    this.response.appendInt(
                            BuildersClubItemIds.toWire(user.getUserInfo().getRoomEntryTeleportId()));
                    this.response.appendInt(user.getUserInfo().getInfostandBorder());
                }
            }
        } else if (this.bot != null) {
            this.response.appendInt(1);
            this.response.appendInt(0 - this.bot.getId());
            this.response.appendString(this.bot.getName());
            this.response.appendString(this.bot.getMotto());
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendString(this.bot.getFigure());
            this.response.appendInt(this.bot.getRoomUnit().getId());
            this.response.appendInt(this.bot.getRoomUnit().getX());
            this.response.appendInt(this.bot.getRoomUnit().getY());
            this.response.appendString(this.bot.getRoomUnit().getZ() + "");
            this.response.appendInt(this.bot.getRoomUnit().getBodyRotation().getValue());
            this.response.appendInt(4);
            this.response.appendString(this.bot.getGender().name().toUpperCase());
            this.response.appendInt(this.bot.getOwnerId());
            this.response.appendString(this.bot.getOwnerName());
            short[] singleActions = this.bot.getOwnerActionIds();
            this.response.appendInt(singleActions.length);
            for (short action : singleActions) {
                this.response.appendShort(action);
            }
            this.response.appendString("unknown");
            this.response.appendInt(0);
            this.response.appendInt(0);
        } else if (this.bots != null) {
            List<Bot> bots = this.bots.stream().filter(Objects::nonNull).toList();
            this.response.appendInt(bots.size());
            for (Bot bot : bots) {
                this.response.appendInt(0 - bot.getId());
                this.response.appendString(bot.getName());
                this.response.appendString(bot.getMotto());
                this.response.appendInt(0);
                this.response.appendInt(0);
                this.response.appendInt(0);
                this.response.appendInt(0);
                this.response.appendString(bot.getFigure());
                this.response.appendInt(bot.getRoomUnit().getId());
                this.response.appendInt(bot.getRoomUnit().getX());
                this.response.appendInt(bot.getRoomUnit().getY());
                this.response.appendString(bot.getRoomUnit().getZ() + "");
                this.response.appendInt(bot.getRoomUnit().getBodyRotation().getValue());
                this.response.appendInt(4);
                this.response.appendString(bot.getGender().name().toUpperCase());
                this.response.appendInt(bot.getOwnerId());
                this.response.appendString(bot.getOwnerName());
                short[] listActions = bot.getOwnerActionIds();
                this.response.appendInt(listActions.length);
                for (short action : listActions) {
                    this.response.appendShort(action);
                }
                this.response.appendString("unknown");
                this.response.appendInt(0);
                this.response.appendInt(0);
            }
        }
        return this.response;
    }

    public User getUser() {
        return user;
    }

    public Collection<User> getUsers() {
        return users;
    }

    public Bot getBot() {
        return bot;
    }

    public Collection<Bot> getBots() {
        return bots;
    }
}
