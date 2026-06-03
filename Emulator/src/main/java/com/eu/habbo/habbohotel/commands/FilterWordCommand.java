package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.modtool.WordFilterWord;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class FilterWordCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterWordCommand.class);

    public FilterWordCommand() {
        super("cmd_filterword", Emulator.getTexts().getValue("commands.keys.cmd_filterword").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_filterword.missing_word"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        String word = params[1];

        // Optional trailing "prefix" keyword marks the word as prefix-only (blocks
        // custom prefixes but not chat/motto/guild). Usage:
        //   :filterword <word>                       -> everywhere, default replacement
        //   :filterword <word> <replacement>         -> everywhere
        //   :filterword <word> prefix                -> prefix-only, default replacement
        //   :filterword <word> <replacement> prefix  -> prefix-only
        boolean prefixOnly = false;
        String replacement = WordFilter.DEFAULT_REPLACEMENT;

        if (params.length >= 3) {
            if (params[params.length - 1].equalsIgnoreCase("prefix")) {
                prefixOnly = true;
                if (params.length >= 4) replacement = params[2];
            } else {
                replacement = params[2];
            }
        }

        WordFilterWord wordFilterWord = new WordFilterWord(word, replacement, prefixOnly);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO wordfilter (`key`, `replacement`, `prefix_only`) VALUES (?, ?, ?)")) {
            statement.setString(1, word);
            statement.setString(2, replacement);
            statement.setString(3, prefixOnly ? "1" : "0");
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_filterword.error"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_filterword.added").replace("%word%", word).replace("%replacement%", replacement) + (prefixOnly ? " [prefix-only]" : ""), RoomChatMessageBubbles.ALERT);
        Emulator.getGameEnvironment().getWordFilter().addWord(wordFilterWord);

        return true;
    }
}