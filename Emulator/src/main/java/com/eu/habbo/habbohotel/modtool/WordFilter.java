package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.friends.FriendChatMessageComposer;
import com.eu.habbo.plugin.events.users.UserTriggerWordFilterEvent;
import gnu.trove.iterator.hash.TObjectHashIterator;
import gnu.trove.set.hash.THashSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.regex.Pattern;

public class WordFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WordFilter.class);

    private static final Pattern DIACRITICS_AND_FRIENDS = Pattern.compile("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");
    public static boolean ENABLED_FRIENDCHAT = true;
    public static String DEFAULT_REPLACEMENT = "bobba";
    protected THashSet<WordFilterWord> autoReportWords = new THashSet<>();
    protected THashSet<WordFilterWord> hideMessageWords = new THashSet<>();
    protected THashSet<WordFilterWord> words = new THashSet<>();

    public WordFilter() {
        long start = System.currentTimeMillis();
        this.reload();
        LOGGER.info("WordFilter -> Loaded! ({} MS)", System.currentTimeMillis() - start);
    }

    @SuppressWarnings("unused")
    private static String stripDiacritics(String str) {
        str = Normalizer.normalize(str, Normalizer.Form.NFD);
        str = DIACRITICS_AND_FRIENDS.matcher(str).replaceAll("");
        return str;
    }

    public synchronized void reload() {
        if (!Emulator.getConfig().getBoolean("hotel.wordfilter.enabled"))
            return;

        this.autoReportWords.clear();
        this.hideMessageWords.clear();
        this.words.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet set = statement.executeQuery("SELECT * FROM wordfilter")) {
                while (set.next()) {
                    WordFilterWord word;

                    try {
                        word = new WordFilterWord(set);
                    } catch (SQLException e) {
                        LOGGER.error("Caught SQL exception", e);
                        continue;
                    }

                    if (!word.prefixOnly) {
                        if (word.autoReport)
                            this.autoReportWords.add(word);
                        else if (word.hideMessage)
                            this.hideMessageWords.add(word);
                    }

                    this.words.add(word);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public String normalise(String message) {
        return DIACRITICS_AND_FRIENDS.matcher(Normalizer.normalize(StringUtils.stripAccents(message), Normalizer.Form.NFKD)
                .replaceAll("[,.;:'\"]", " ").replace("I", "l")
                .replaceAll("[^\\p{ASCII}*$]", "").replaceAll("\\p{M}", " ")
                .replaceAll("^\\p{M}*$]", "").replaceAll("[1|]", "i")
                .replace("2", "z").replace("3", "e")
                .replace("4", "a").replace("5", "s")
                .replace("8", "b").replace("0", "o")
                .replace(" ", " ").replace("$", "s")
                .replace("ß", "b").trim()).replaceAll(" ");
    }

    public boolean autoReportCheck(RoomChatMessage roomChatMessage) {
        String message = this.normalise(roomChatMessage.getMessage()).toLowerCase();

        TObjectHashIterator<WordFilterWord> iterator = this.autoReportWords.iterator();

        while (iterator.hasNext()) {
            WordFilterWord word = (WordFilterWord) iterator.next();

            if (message.contains(word.key)) {
                Emulator.getGameEnvironment().getModToolManager().quickTicket(roomChatMessage.getHabbo(), "Automatic WordFilter", roomChatMessage.getMessage());

                if (Emulator.getConfig().getBoolean("notify.staff.chat.auto.report")) {
                    Emulator.getGameEnvironment().getHabboManager().sendPacketToHabbosWithPermission(new FriendChatMessageComposer(new Message(roomChatMessage.getHabbo().getHabboInfo().getId(), 0, Emulator.getTexts().getValue("warning.auto.report").replace("%user%", roomChatMessage.getHabbo().getHabboInfo().getUsername()).replace("%word%", word.key))).compose(), "acc_staff_chat");
                }
                return true;
            }
        }

        return false;
    }

    public boolean hideMessageCheck(String message) {
        message = this.normalise(message).toLowerCase();

        TObjectHashIterator<WordFilterWord> iterator = this.hideMessageWords.iterator();

        while (iterator.hasNext()) {
            WordFilterWord word = (WordFilterWord) iterator.next();

            if (message.contains(word.key)) {
                return true;
            }
        }

        return false;
    }

    public String[] filter(String[] messages) {
        for (int i = 0; i < messages.length; i++) {
            messages[i] = this.filter(messages[i], null);
        }

        return messages;
    }

    public String filter(String message, Habbo habbo) {
        String filteredMessage = message;
        if (Emulator.getConfig().getBoolean("hotel.wordfilter.normalise")) {
            filteredMessage = this.normalise(filteredMessage);
        }

        TObjectHashIterator<WordFilterWord> iterator = this.words.iterator();

        boolean foundShit = false;

        while (iterator.hasNext()) {
            WordFilterWord word = (WordFilterWord) iterator.next();

            if (word.prefixOnly) continue;

            if (StringUtils.containsIgnoreCase(filteredMessage, word.key)) {
                if (habbo != null) {
                    if (Emulator.getPluginManager().fireEvent(new UserTriggerWordFilterEvent(habbo, word)).isCancelled())
                        continue;
                }
                filteredMessage = filteredMessage.replace("(?i)" + word.key, word.replacement);
                foundShit = true;

                if (habbo != null && word.muteTime > 0) {
                    habbo.mute(word.muteTime, false);
                }
            }
        }

        if (!foundShit) {
            return message;
        }

        return filteredMessage;
    }

    public void filter(RoomChatMessage roomChatMessage, Habbo habbo) {
        String message = roomChatMessage.getMessage().toLowerCase();

        if (Emulator.getConfig().getBoolean("hotel.wordfilter.normalise")) {
            message = this.normalise(message);
        }

        TObjectHashIterator<WordFilterWord> iterator = this.words.iterator();

        while (iterator.hasNext()) {
            WordFilterWord word = (WordFilterWord) iterator.next();

            if (word.prefixOnly) continue;

            if (StringUtils.containsIgnoreCase(message, word.key)) {
                if (habbo != null) {
                    if (Emulator.getPluginManager().fireEvent(new UserTriggerWordFilterEvent(habbo, word)).isCancelled())
                        continue;
                }

                message = message.replace(word.key, word.replacement);
                roomChatMessage.filtered = true;
            }
        }

        if (roomChatMessage.filtered) {
            roomChatMessage.setMessage(message);
        }
    }

    public THashSet<WordFilterWord> getWords() {
        return new THashSet<>(this.words);
    }

    public void addWord(WordFilterWord word) {
        this.words.add(word);
    }
}
