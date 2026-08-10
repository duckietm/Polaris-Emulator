package com.eu.habbo.messages.outgoing.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class AvailableCommandsComposer extends MessageComposer {
    private final List<Command> commands;

    public AvailableCommandsComposer(List<Command> commands) {
        this.commands = commands;
    }

    @Override
    protected ServerMessage composeInternal() {
        List<Command> uniqueCommands = distinctByPrimaryKey(this.commands);

        this.response.init(Outgoing.AvailableCommandsComposer);
        this.response.appendInt(uniqueCommands.size());

        for (Command cmd : uniqueCommands) {
            this.response.appendString(cmd.keys[0]);
            this.response.appendString(
                    Emulator.getTexts().getValueQuietly("commands.description." + cmd.permission, cmd.permission));
        }

        return this.response;
    }

    static List<Command> distinctByPrimaryKey(List<Command> commands) {
        LinkedHashMap<String, Command> byPrimaryKey = new LinkedHashMap<>();

        if (commands != null) {
            for (Command command : commands) {
                if (command == null || command.keys == null || command.keys.length == 0) continue;

                String primaryKey = command.keys[0];

                if (primaryKey == null || primaryKey.isEmpty()) continue;

                byPrimaryKey.putIfAbsent(primaryKey, command);
            }
        }

        return new ArrayList<>(byPrimaryKey.values());
    }
}
