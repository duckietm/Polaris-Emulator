package com.eu.habbo.messages.incoming.rooms.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BotErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.BotForceOpenContextMenuComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDanceComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.eu.habbo.messages.outgoing.unknown.UnknownRoomDesktopComposer;
import com.eu.habbo.plugin.events.bots.BotSavedChatEvent;
import com.eu.habbo.plugin.events.bots.BotSavedLookEvent;
import com.eu.habbo.plugin.events.bots.BotSavedNameEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;

public class BotSaveSettingsEvent extends MessageHandler {
    static final String REOPEN_MENU_SETTING = "hotel.bots.menu.reopen_after_action";

    /** Skills AIR turns into data-driven buttons (in-client link, NUX proceed, navigator search). */
    private static final Set<Integer> DATA_SKILLS = Set.of(7, 8, 14);

    /**
     * The bot's skills as a BotSkillListUpdate list. AIR replaces the bot's skills with this list and
     * renders a button for each data skill, so those are left out: they carry no data here and would
     * show an empty "proceed" button.
     */
    static Map<Integer, String> menuSkills(short[] ownerActionIds) {
        Map<Integer, String> skills = new LinkedHashMap<>();
        if (ownerActionIds == null) return skills;
        for (short id : ownerActionIds) {
            if (!DATA_SKILLS.contains((int) id)) skills.put((int) id, "");
        }
        return skills;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId()
                || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            int botId = this.packet.readInt();
            Bot bot = room.getBot(Math.abs(botId));
            if (bot == null) return;
            int settingId = this.packet.readInt();
            boolean allowed = false;
            for (short a : bot.getOwnerActionIds()) {
                if (a == settingId) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) return;

            if (!bot.tryAcquireOwnerActionSlot()) return;

            boolean reopenMenu = false;
            ConfigurationManager config = Emulator.getConfig();

            switch (settingId) {
                case 1:
                    BotSavedLookEvent lookEvent = new BotSavedLookEvent(
                            bot,
                            this.client.getHabbo().getHabboInfo().getGender(),
                            this.client.getHabbo().getHabboInfo().getLook(),
                            this.client.getHabbo().getRoomUnit().getEffectId());
                    Emulator.getPluginManager().fireEvent(lookEvent);

                    if (lookEvent.isCancelled()) break;

                    bot.setFigure(lookEvent.newLook);
                    bot.setGender(lookEvent.gender);
                    bot.setEffect(lookEvent.effect, -1);
                    bot.needsUpdate(true);
                    reopenMenu = true;
                    break;

                case 2:
                    String messageString = this.packet.readString();

                    if (messageString.length() > 5112) break;

                    String[] data = messageString.split(";#;");

                    ArrayList<String> chat = new ArrayList<>();
                    int totalChatLength = 0;
                    for (int i = 0; i < data.length - 3 && totalChatLength <= 120; i++) {
                        for (String s : data[i].split("\r")) {
                            String filtered = Jsoup.parse(s).text();
                            int count = 0;
                            while (!filtered.equalsIgnoreCase(s)) {
                                if (count >= 5) {
                                    bot.clearChat();
                                    return;
                                }
                                s = filtered;
                                filtered = Jsoup.parse(s).text();
                                count++;
                            }

                            String result = Emulator.getGameEnvironment()
                                    .getWordFilter()
                                    .filter(s, null);

                            if (!result.isEmpty()) {
                                if (!this.client.getHabbo().hasPermission(Permission.ACC_CHAT_NO_FILTER)) {
                                    result = Emulator.getGameEnvironment()
                                            .getWordFilter()
                                            .filter(result, this.client.getHabbo());
                                }

                                result = result.substring(
                                        0, Math.min(BotManager.MAXIMUM_CHAT_LENGTH - totalChatLength, result.length()));
                                chat.add(result);
                                totalChatLength += result.length();
                            }
                        }
                    }

                    int chatSpeed = 7;

                    try {
                        chatSpeed = clampChatSpeed(Integer.parseInt(data[data.length - 2]));
                    } catch (Exception e) {
                        // Invalid chatspeed. Use 7.
                    }

                    BotSavedChatEvent chatEvent = new BotSavedChatEvent(
                            bot,
                            Boolean.parseBoolean(data[data.length - 3]),
                            Boolean.parseBoolean(data[data.length - 1]),
                            chatSpeed,
                            chat);
                    Emulator.getPluginManager().fireEvent(chatEvent);

                    if (chatEvent.isCancelled()) break;

                    bot.setChatAuto(chatEvent.autoChat);
                    bot.setChatRandom(chatEvent.randomChat);
                    // Plugins may rewrite the delay; clamp again so the cast cannot wrap.
                    bot.setChatDelay((short) clampChatSpeed(chatEvent.chatDelay));
                    bot.clearChat();
                    bot.addChatLines(chat);
                    bot.needsUpdate(true);
                    break;

                case 3:
                    bot.setCanWalk(!bot.canWalk());
                    bot.needsUpdate(true);
                    reopenMenu = true;
                    break;

                case 4:
                    bot.getRoomUnit()
                            .setDanceType(
                                    DanceType.values()[
                                            (bot.getRoomUnit().getDanceType().getType() + 1)
                                                    % DanceType.values().length]);
                    room.sendComposer(new RoomUserDanceComposer(bot.getRoomUnit()).compose());
                    bot.needsUpdate(true);
                    reopenMenu = true;
                    break;

                case 5:
                    String name = this.packet.readString();
                    boolean invalidName =
                            name.length() > BotManager.MAXIMUM_NAME_LENGTH || name.contains("<") || name.contains(">");
                    if (!invalidName) {
                        String filteredName =
                                Emulator.getGameEnvironment().getWordFilter().filter(name, null);
                        invalidName = !name.equalsIgnoreCase(filteredName);
                        if (!invalidName) {
                            BotSavedNameEvent nameEvent = new BotSavedNameEvent(bot, name);

                            Emulator.getPluginManager().fireEvent(nameEvent);

                            if (nameEvent.isCancelled()) break;

                            bot.setName(nameEvent.name);
                            bot.needsUpdate(true);
                            room.sendComposer(new RoomUserNameChangedComposer(
                                            bot.getRoomUnit().getId(),
                                            bot.getRoomUnit().getId(),
                                            nameEvent.name)
                                    .compose());
                        }
                    }

                    if (invalidName) {
                        this.client.sendResponse(
                                new BotErrorComposer(BotErrorComposer.ROOM_ERROR_BOTS_NAME_NOT_ACCEPT));
                    }
                    break;
                case 9:
                    String motto = this.packet.readString();

                    if (motto.length() > config.getInt("motto.max_length", 38)) break;

                    bot.setMotto(motto);
                    bot.needsUpdate(true);
                    room.sendComposer(new RoomUsersComposer(bot).compose());
                    break;
                case Bot.ACTION_ROTATE:
                    if (bot.getRoomUnit() == null) break;
                    int next = (bot.getRoomUnit().getBodyRotation().getValue() + 2) % 8;
                    RoomUserRotation rotation = RoomUserRotation.fromValue(next);
                    bot.getRoomUnit().setRotation(rotation);
                    bot.needsUpdate(true);
                    room.sendComposer(new RoomUserStatusComposer(bot.getRoomUnit()).compose());
                    break;
            }

            bot.onPostOwnerAction(settingId);

            // AIR closes the bot menu on a dress up / random walk / dance click; the server answers with the
            // bot's skills (BotSkillListUpdate) and BotForceOpenContextMenu, which AIR's avatar info widget
            // uses to reopen the menu of the bot that was last selected, so the owner can keep toggling.
            if (reopenMenu && config.getBoolean(REOPEN_MENU_SETTING, true)) {
                this.client.sendResponse(
                        new UnknownRoomDesktopComposer(-bot.getId(), menuSkills(bot.getOwnerActionIds())));
                this.client.sendResponse(new BotForceOpenContextMenuComposer(-bot.getId()));
            }

            if (bot.needsUpdate()) {
                Emulator.getThreading().run(bot);
            }
        }
    }

    /**
     * Keeps a client-supplied chat delay within the configured bounds and
     * within what the bot's short-typed delay can hold, so the narrowing cast
     * never wraps a huge value into a negative or tiny one.
     */
    static int clampChatSpeed(int chatSpeed) {
        int max = Math.min(BotManager.MAXIMUM_CHAT_SPEED, Short.MAX_VALUE);
        if (chatSpeed < BotManager.MINIMUM_CHAT_SPEED) return BotManager.MINIMUM_CHAT_SPEED;
        if (chatSpeed > max) return max;
        return chatSpeed;
    }
}
