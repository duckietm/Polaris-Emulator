package com.eu.habbo.messages.outgoing.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class AvailableCommandsComposer extends MessageComposer {
    private final List<Command> commands;

    public AvailableCommandsComposer(List<Command> commands) {
        this.commands = commands;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AvailableCommandsComposer);
        this.response.appendInt(this.commands.size());

        for (Command cmd : this.commands) {
            this.response.appendString(cmd.keys[0]);
            this.response.appendString(
                    Emulator.getTexts().getValueQuietly("commands.description." + cmd.permission, cmd.permission)
            );
        }

        return this.response;
    }
}
