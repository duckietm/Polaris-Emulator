package polaris.messages.incoming.guilds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildBadgeBuilder;
import polaris.hotel.guilds.GuildBadgeInput;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.guilds.GuildBoughtComposer;
import polaris.messages.outgoing.guilds.GuildEditFailComposer;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.rooms.RoomDataComposer;
import polaris.plugin.events.guilds.GuildPurchasedEvent;
import polaris.util.imager.badges.BadgeImager;

public class RequestGuildBuyEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestGuildBuyEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        final String name =
                Emulator.getGameEnvironment().getWordFilter().filter(this.packet.readString(), this.client.getUser());
        final String description =
                Emulator.getGameEnvironment().getWordFilter().filter(this.packet.readString(), this.client.getUser());

        if (!GuildInputLimits.isValidGuildName(name)) {
            this.client.sendResponse(new GuildEditFailComposer(GuildEditFailComposer.INVALID_GUILD_NAME));
            return;
        }
        if (!GuildInputLimits.isValidGuildDescription(description)) {
            return;
        }

        if (Emulator.getConfig().getBoolean("catalog.guild.hc_required", true)
                && !this.client.getUser().getUserStats().hasActiveClub()) {
            this.client.sendResponse(new GuildEditFailComposer(GuildEditFailComposer.HC_REQUIRED));
            return;
        }

        int roomId = this.packet.readInt();

        if (!GuildInputGuard.isPositiveId(roomId)) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        Room r = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (r == null) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (r.hasGuild() || r.getGuildId() != 0) {
            this.client.sendResponse(new GuildEditFailComposer(GuildEditFailComposer.ROOM_ALREADY_IN_USE));
            return;
        }

        if (r.getOwnerId() != this.client.getUser().getUserInfo().getId()) {
            String message = Emulator.getTexts()
                    .getValue("scripter.warning.guild.buy.owner")
                    .replace("%username%", this.client.getUser().getUserInfo().getUsername())
                    .replace("%roomname%", r.getName().replace("%owner%", r.getOwnerName()));
            ScripterManager.scripterDetected(this.client, message);
            LOGGER.info(message);
            return;
        }

        int colorOne = this.packet.readInt();
        int colorTwo = this.packet.readInt();

        if (!Emulator.getGameEnvironment().getGuildManager().symbolColor(colorOne)
                || !Emulator.getGameEnvironment().getGuildManager().backgroundColor(colorTwo)) {
            this.client.sendResponse(new GuildEditFailComposer(GuildEditFailComposer.INVALID_GUILD_NAME));
            return;
        }

        int count = this.packet.readInt();

        ConfigurationManager config = Emulator.getConfig();
        BadgeImager imager = Emulator.getBadgeImager();
        String badge = GuildBadgeBuilder.readBadge(this.packet, count);
        if (badge == null || !GuildBadgeInput.isAcceptable(badge, config, imager)) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        // Only charge the player once every step has been validated. Previously the
        // credits were deducted before the room was checked, so a purchase that
        // failed afterwards (missing room, room already used by a guild, not the
        // owner) still took the credits without ever creating the group.
        // The debit says whether it happened (a plugin or the ledger can refuse it), and a group that could not be
        // created gives the price back: the price used to be taken with no check and kept when the creation failed.
        int charged = 0;
        if (!this.client.getUser().hasPermission(Permission.ACC_INFINITE_CREDITS)) {
            int guildPrice = Math.max(0, config.getInt("catalog.guild.price"));
            if (guildPrice > 0) {
                if (this.client.getUser().getUserInfo().getCredits() < guildPrice
                        || !this.client.getUser().tryTakeCredits(guildPrice)) {
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    return;
                }
                charged = guildPrice;
            }
        }

        Guild guild = Emulator.getGameEnvironment()
                .getGuildManager()
                .createGuild(this.client.getUser(), roomId, r.getName(), name, description, badge, colorOne, colorTwo);

        if (guild == null) {
            if (charged > 0) {
                this.client.getUser().giveCredits(charged, "guild.purchase.refund");
            }
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        r.setGuild(guild.getId());
        r.removeAllRights();
        r.setNeedsUpdate(true);

        Emulator.getGameEnvironment().getGuildManager().addGuild(guild);

        if (imager != null && config.getBoolean("imager.internal.enabled")) {
            imager.generate(guild);
        }

        this.client.sendResponse(new PurchaseOKComposer());
        this.client.sendResponse(new GuildBoughtComposer(guild));
        r.refreshGuild(guild);

        for (User user : r.getUsers()) {
            if (user.getClient() == null) {
                continue;
            }

            user.getClient().sendResponse(new GuildInfoComposer(guild, user.getClient(), false, null));

            if (user.getUserInfo().getId()
                    != this.client.getUser().getUserInfo().getId()) {
                user.getClient().sendResponse(new RoomDataComposer(r, user, true, false));
            }
        }

        Emulator.getPluginManager().fireEvent(new GuildPurchasedEvent(guild, this.client.getUser()));
    }
}
