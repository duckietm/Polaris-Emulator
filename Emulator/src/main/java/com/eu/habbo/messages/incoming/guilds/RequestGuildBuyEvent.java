package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildBadgeBuilder;
import com.eu.habbo.habbohotel.guilds.GuildBadgeInput;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildBoughtComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildEditFailComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomDataComposer;
import com.eu.habbo.plugin.events.guilds.GuildPurchasedEvent;
import com.eu.habbo.util.imager.badges.BadgeImager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestGuildBuyEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestGuildBuyEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        final String name =
                Emulator.getGameEnvironment().getWordFilter().filter(this.packet.readString(), this.client.getHabbo());
        final String description =
                Emulator.getGameEnvironment().getWordFilter().filter(this.packet.readString(), this.client.getHabbo());

        if (!GuildInputLimits.isValidGuildName(name)) {
            this.client.sendResponse(new GuildEditFailComposer(GuildEditFailComposer.INVALID_GUILD_NAME));
            return;
        }
        if (!GuildInputLimits.isValidGuildDescription(description)) {
            return;
        }

        if (Emulator.getConfig().getBoolean("catalog.guild.hc_required", true)
                && !this.client.getHabbo().getHabboStats().hasActiveClub()) {
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

        if (r.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()) {
            String message = Emulator.getTexts()
                    .getValue("scripter.warning.guild.buy.owner")
                    .replace("%username%", this.client.getHabbo().getHabboInfo().getUsername())
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
        if (!this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_CREDITS)) {
            int guildPrice = Math.max(0, config.getInt("catalog.guild.price"));
            if (guildPrice > 0) {
                if (this.client.getHabbo().getHabboInfo().getCredits() < guildPrice
                        || !this.client.getHabbo().tryTakeCredits(guildPrice)) {
                    this.client.sendResponse(
                            new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    return;
                }
                charged = guildPrice;
            }
        }

        Guild guild = Emulator.getGameEnvironment()
                .getGuildManager()
                .createGuild(this.client.getHabbo(), roomId, r.getName(), name, description, badge, colorOne, colorTwo);

        if (guild == null) {
            if (charged > 0) {
                this.client.getHabbo().giveCredits(charged, "guild.purchase.refund");
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

        for (Habbo habbo : r.getHabbos()) {
            if (habbo.getClient() == null) {
                continue;
            }

            habbo.getClient().sendResponse(new GuildInfoComposer(guild, habbo.getClient(), false, null));

            if (habbo.getHabboInfo().getId()
                    != this.client.getHabbo().getHabboInfo().getId()) {
                habbo.getClient().sendResponse(new RoomDataComposer(r, habbo, true, false));
            }
        }

        Emulator.getPluginManager().fireEvent(new GuildPurchasedEvent(guild, this.client.getHabbo()));
    }
}
